// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.ime.KeyboardController
import com.qtekfun.ultimatekeys.ime.R
import com.qtekfun.ultimatekeys.ime.logic.EnterKind
import com.qtekfun.ultimatekeys.ime.panels.PanelHost
import com.qtekfun.ultimatekeys.ime.panels.PanelKind
import com.qtekfun.ultimatekeys.ime.panels.PanelTheme
import com.qtekfun.ultimatekeys.ime.voice.VoicePanel
import com.qtekfun.ultimatekeys.style.MicPlacement
import com.qtekfun.ultimatekeys.style.PanelTransition
import com.qtekfun.ultimatekeys.style.ToolIcons
import com.qtekfun.ultimatekeys.voice.DictationState
import kotlinx.coroutines.flow.MutableStateFlow

/** The keyboard: a strip reserved for suggestions (Phase 2) above a single-canvas key grid. */
@Composable
fun KeyboardSurface(controller: KeyboardController, modifier: Modifier = Modifier) {
    val state by controller.logic.state.collectAsState()
    val strip by controller.suggestions.state.collectAsState()
    val settings by controller.settings.collectAsState()
    val privacy by controller.privacy.state.collectAsState()
    val density = LocalDensity.current
    val activeStyle by controller.style.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val tones = remember(context) { ToneSource.fromContext(context) }
    val style = remember(activeStyle, systemDark, tones) {
        SurfaceStyle.resolve(activeStyle, systemDark, tones)
    }
    val labels = rememberLabels()
    val description = stringResource(R.string.keyboard_description)
    val privateOn = stringResource(R.string.private_mode_on)
    val privateAction = stringResource(R.string.private_mode_toggle)
    val globeMenu = listOf(
        stringResource(R.string.globe_menu_settings),
        stringResource(R.string.globe_menu_keyboards)
    )
    val screenReader = rememberAccessibilityActive()
    val panelKind by controller.panels.kind.collectAsState()

    val layout =
        remember(
            state.page,
            settings.letterLayoutId,
            settings.numberRow,
            activeStyle.bottomRow,
            controller.features
        ) {
            controller.layoutFor(state.page, settings, activeStyle)
        }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var presses by remember { mutableStateOf(emptyList<PressView>()) }

    val rowDp = SurfaceSpec.rowDp(settings.heightPercent)
    val dimens = with(density) {
        SurfaceDimens(
            gapX = style.gapXDp.dp.toPx(),
            gapY = style.gapYDp.dp.toPx(),
            corner = style.cornerDp.dp.toPx(),
            borderWidth = style.borderWidthDp.dp.toPx(),
            elevation = style.elevationDp.dp.toPx(),
            labelSize = (rowDp * LABEL_RATIO * style.labelSizePercent / PERCENT).dp.toPx(),
            hintSize = 10.dp.toPx(),
            previewHeight = (rowDp * PREVIEW_RATIO).dp.toPx()
        )
    }
    val geometry =
        remember(
            layout,
            widthPx,
            settings.heightPercent,
            settings.numberRow,
            activeStyle,
            density
        ) {
            SurfaceSpec.geometry(
                layout,
                widthPx,
                density.density,
                settings.heightPercent,
                activeStyle,
                settings.numberRow
            )
        }

    val showToggle = privacy.isPrivate || activeStyle.suggestionBar.toolIcons != ToolIcons.HIDDEN
    val showMic = controller.features.voice &&
        activeStyle.bottomRow.micPlacement == MicPlacement.SUGGESTION_BAR
    val dictation = controller.dictation
    val dictationState by (dictation?.state ?: IdleDictation).collectAsState()
    val dictating = dictation != null && dictationState != DictationState.Idle
    val showTools = activeStyle.suggestionBar.toolIcons == ToolIcons.SHOWN
    val micInMargin = SurfaceSpec.micInMargin(controller.features.voice, activeStyle)
    val scope = rememberCoroutineScope()
    val gestures = remember(controller) { SurfaceGestures(controller, scope) { presses = it } }
    gestures.geometry = geometry
    gestures.stripToggleVisible = showToggle
    gestures.stripMicVisible = showMic
    gestures.stripToolsVisible = showTools
    gestures.metrics = with(density) {
        GestureMetrics(
            slop = 12.dp.toPx(),
            dragStep = 14.dp.toPx(),
            chooserMinCell = 44.dp.toPx(),
            chooserHeight = (rowDp * PREVIEW_RATIO).dp.toPx()
        )
    }
    DisposableEffect(layout) { onDispose { gestures.cancelAll() } }
    // Fingers still down on the keys must not type into the panel that replaces them.
    LaunchedEffect(dictating) { gestures.cancelAll() }

    val fonts = remember(context) { FontCatalog(context.assets) }
    val renderer = remember(fonts) { SurfaceRenderer(fonts) }
    val panelTheme = remember(activeStyle, systemDark, tones, fonts, rowDp) {
        PanelTheme.resolve(activeStyle, systemDark, tones, fonts, rowDp)
    }
    val keysHeightDp = SurfaceSpec.totalHeightDp(
        layout.rows.size,
        settings.heightPercent,
        settings.bottomMarginDp,
        activeStyle,
        settings.numberRow,
        micInMargin
    )
    val marginMic = MarginMic.forSurface(
        micInMargin,
        widthPx,
        geometry.top + geometry.height,
        keysHeightDp * density.density,
        settings.dictationMicSide == KeyboardSettings.MIC_LEFT
    )
    gestures.marginMic = marginMic
    gestures.globeMenu = globeMenu
    // The system bar area below the keys carries the keyboard's own background, like a margin.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(style.background)
            .navigationBarsPadding()
    ) {
        val totalHeight = keysHeightDp.dp
        val fadeMs = if (activeStyle.motion.transition == PanelTransition.NONE) {
            0
        } else {
            activeStyle.motion.durationMs
        }
        Crossfade(
            targetState = dictating,
            animationSpec = tween(fadeMs),
            label = "panel"
        ) { panel ->
            if (panel && dictation != null) {
                VoicePanel(
                    host = dictation,
                    style = activeStyle,
                    isPrivate = privacy.isPrivate,
                    modifier = Modifier.height(totalHeight)
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalHeight)
                        .onSizeChanged { widthPx = it.width.toFloat() }
                        // With a screen reader the virtual key nodes replace the canvas's own.
                        .keyboardSemantics(
                            hidden = screenReader || panelKind != PanelKind.NONE,
                            description = description,
                            privateState = privateOn.takeIf { privacy.isPrivate },
                            toggle = privateAction.takeIf { controller.privacy.canToggle },
                            onToggle = controller::togglePrivate
                        )
                        .pointerInput(gestures) { trackPointers(gestures) }
                ) {
                    renderer.draw(
                        this,
                        geometry,
                        state,
                        presses,
                        style,
                        labels,
                        dimens,
                        StripState(
                            strip.slots,
                            privacy.isPrivate,
                            showToggle,
                            showMic,
                            showTools,
                            marginMic
                        )
                    )
                }
            }
        }
        if (screenReader && !dictating && panelKind == PanelKind.NONE) {
            KeyboardAccessibilityOverlay(
                controller = controller,
                geometry = geometry,
                state = state,
                strip = A11yStrip(
                    suggestions = strip,
                    toggle = showToggle,
                    privateOn = privacy.isPrivate,
                    canTogglePrivate = controller.privacy.canToggle,
                    mic = showMic,
                    tools = showTools,
                    marginMic = marginMic
                ),
                modifier = Modifier.fillMaxWidth().height(totalHeight)
            )
        }
        PanelHost(controller, panelTheme, keysHeightDp - settings.bottomMarginDp)
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.trackPointers(
    gestures: SurfaceGestures
) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            event.changes.forEach { change ->
                val id = change.id.value
                when {
                    change.changedToDown() -> gestures.down(
                        id,
                        change.position.x,
                        change.position.y
                    )

                    change.changedToUp() -> gestures.up(id, change.position.x)

                    change.pressed -> gestures.move(id, change.position.x, change.position.y)
                }
                change.consume()
            }
        }
    }
}

@Composable
private fun rememberLabels(): SurfaceLabels {
    val enter = mapOf(
        EnterKind.ENTER to stringResource(R.string.key_enter),
        EnterKind.GO to stringResource(R.string.key_go),
        EnterKind.SEARCH to stringResource(R.string.key_search),
        EnterKind.SEND to stringResource(R.string.key_send),
        EnterKind.NEXT to stringResource(R.string.key_next),
        EnterKind.DONE to stringResource(R.string.key_done),
        EnterKind.PREVIOUS to stringResource(R.string.key_previous)
    )
    return SurfaceLabels(
        enter = enter,
        space = stringResource(R.string.key_space),
        symbols = "?123",
        letters = "ABC",
        moreSymbols = "=\\<"
    )
}

private val IdleDictation = MutableStateFlow<DictationState>(DictationState.Idle)

private const val LABEL_RATIO = 0.4f
private const val PERCENT = 100f
private const val PREVIEW_RATIO = 1.0f
