// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qtekfun.ultimatekeys.ime.R
import com.qtekfun.ultimatekeys.ime.surface.ToneSource
import com.qtekfun.ultimatekeys.style.PanelTransition
import com.qtekfun.ultimatekeys.style.Style
import com.qtekfun.ultimatekeys.voice.DictationError
import com.qtekfun.ultimatekeys.voice.DictationState
import kotlin.math.sin

/**
 * The dictation panel as the keyboard shows it: reads the host's state, animates the meter and the
 * waiting indicator with the style's motion settings and forwards the buttons to the host.
 */
@Composable
fun VoicePanel(
    host: DictationHost,
    style: Style,
    isPrivate: Boolean,
    modifier: Modifier = Modifier
) {
    val state by host.state.collectAsState()
    val refused by host.permissionRefused.collectAsState()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val tones = remember(context) { ToneSource.fromContext(context) }
    val colors = remember(style, dark, tones) { VoicePanelColors.resolve(style, dark, tones) }

    val animated = style.motion.transition != PanelTransition.NONE && style.motion.durationMs > 0
    val target = (state as? DictationState.Listening)?.level ?: 0f
    val level by animateFloatAsState(
        targetValue = target,
        animationSpec = if (animated) spring(stiffness = Spring.StiffnessMedium) else tween(0),
        label = "level"
    )
    val phase = if (animated) rememberPhase() else 0f

    val error = (state as? DictationState.Failed)?.error
    val action = error?.let { host.actionFor(it, refused) }
    VoicePanelContent(
        state = state,
        action = action,
        isPrivate = isPrivate,
        colors = colors,
        cornerDp = style.panels.cornerRadiusDp,
        level = level,
        phase = phase,
        onStop = host::stop,
        onCancel = {
            if (error != null) host.dismiss() else host.cancel()
        },
        onAction = host::perform,
        modifier = modifier
    )
}

@Composable
private fun rememberPhase(): Float {
    val transition = rememberInfiniteTransition(label = "voice")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(PHASE_MS, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "phase"
    )
    return phase
}

/**
 * The panel without any state of its own, so that screenshot tests can draw every state.
 * [level] is the meter value (0..1) and [phase] the repeating animation position (0..1).
 */
// A stateless panel takes everything it draws and every button it offers.
@Suppress("LongParameterList")
@Composable
fun VoicePanelContent(
    state: DictationState,
    action: PanelAction?,
    isPrivate: Boolean,
    colors: VoicePanelColors,
    cornerDp: Float,
    level: Float,
    phase: Float,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onAction: (PanelAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.voice_panel_description)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .background(
                if (isPrivate) colors.privateTint.copy(alpha = PRIVATE_WASH) else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PanelText(
            text = titleFor(state),
            style = TextStyle(
                color = colors.text,
                fontSize = TITLE_SP.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .padding(top = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Orb(state, colors, level, phase, onStop)
        }
        PanelText(
            text = hintFor(state, isPrivate),
            style = TextStyle(
                color = colors.hint,
                fontSize = HINT_SP.sp,
                textAlign = TextAlign.Center
            )
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (action != null) {
                Pill(labelFor(action), colors, cornerDp, filled = true) { onAction(action) }
            }
            if (state !is DictationState.Idle) {
                val cancelLabel = if (state is DictationState.Failed) {
                    stringResource(R.string.voice_close)
                } else {
                    stringResource(R.string.voice_cancel)
                }
                Pill(cancelLabel, colors, cornerDp, filled = false, onClick = onCancel)
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
private fun PanelText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(text = text, style = style, modifier = modifier)
}

/** The round microphone button with the meter rings around it, or the waiting indicator. */
@Composable
private fun Orb(
    state: DictationState,
    colors: VoicePanelColors,
    level: Float,
    phase: Float,
    onStop: () -> Unit
) {
    val listening = state is DictationState.Listening
    val speaking = (state as? DictationState.Listening)?.speaking == true
    val busy = state is DictationState.Transcribing
    val stopLabel = stringResource(R.string.voice_stop)
    val core = if (listening || busy) colors.accent else colors.surface
    val ink = if (listening || busy) colors.onAccent else colors.text
    val modifier = Modifier
        .size(ORB_DP.dp)
        .let {
            if (listening) {
                it
                    .clickable(onClickLabel = stopLabel, role = Role.Button, onClick = onStop)
                    .semantics { contentDescription = stopLabel }
            } else {
                it
            }
        }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            // On a short keyboard the button shrinks with the room it is given.
            val coreRadius = (size.minDimension * CORE_FRACTION).coerceAtMost(CORE_DP.dp.toPx())
            val reach = size.minDimension / 2f - coreRadius
            if (listening) {
                // The meter: two soft rings that swell with the voice and breathe a little.
                val breathe = (1f + sin(phase * TWO_PI)) * HALF
                val grow = reach * (RING_BASE + RING_LEVEL * level + RING_BREATH * breathe)
                drawCircle(
                    colors.accent.copy(alpha = if (speaking) RING_ALPHA_LOUD else RING_ALPHA),
                    coreRadius + grow,
                    center
                )
                drawCircle(
                    colors.accent.copy(alpha = RING_ALPHA_INNER),
                    coreRadius + grow * INNER_RING,
                    center
                )
            }
            drawCircle(core, coreRadius, center)
            if (busy) {
                val stroke = Stroke(width = SPINNER_DP.dp.toPx(), cap = StrokeCap.Round)
                val radius = coreRadius + SPINNER_GAP_DP.dp.toPx()
                drawArc(
                    colors.accent,
                    startAngle = phase * FULL_TURN - QUARTER_TURN,
                    sweepAngle = SPINNER_SWEEP,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = stroke
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.ms_mic),
            contentDescription = null,
            modifier = Modifier.size(ICON_DP.dp),
            colorFilter = ColorFilter.tint(ink)
        )
    }
}

@Composable
private fun Pill(
    label: String,
    colors: VoicePanelColors,
    cornerDp: Float,
    filled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(cornerDp.coerceAtMost(PILL_MAX_CORNER_DP).dp)
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = TARGET_DP.dp, minWidth = TARGET_DP.dp)
            .background(if (filled) colors.accent else colors.surface, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        PanelText(
            text = label,
            style = TextStyle(
                color = if (filled) colors.onAccent else colors.text,
                fontSize = BUTTON_SP.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun titleFor(state: DictationState): String = when (state) {
    is DictationState.Listening -> stringResource(R.string.voice_listening)
    DictationState.Transcribing -> stringResource(R.string.voice_transcribing)
    is DictationState.Failed -> errorText(state.error)
    DictationState.Idle -> ""
}

@Composable
private fun hintFor(state: DictationState, isPrivate: Boolean): String = when {
    state is DictationState.Listening && state.speaking -> stringResource(
        R.string.voice_speaking_hint
    )

    state is DictationState.Listening -> stringResource(R.string.voice_listening_hint)

    isPrivate -> stringResource(R.string.voice_private_hint)

    else -> ""
}

@Composable
private fun errorText(error: DictationError): String = stringResource(
    when (error) {
        DictationError.NO_PERMISSION -> R.string.voice_error_no_permission
        DictationError.NO_MODEL -> R.string.voice_error_no_model
        DictationError.MODEL_LOAD_FAILED -> R.string.voice_error_model_load
        DictationError.MIC_UNAVAILABLE -> R.string.voice_error_mic
        DictationError.NO_SPEECH -> R.string.voice_error_no_speech
        DictationError.TRANSCRIPTION_FAILED -> R.string.voice_error_failed
    }
)

@Composable
private fun labelFor(action: PanelAction): String = stringResource(
    when (action) {
        PanelAction.ALLOW_MICROPHONE -> R.string.voice_action_allow
        PanelAction.OPEN_SETTINGS -> R.string.voice_action_settings
        PanelAction.OPEN_MODELS -> R.string.voice_action_models
        PanelAction.TRY_AGAIN -> R.string.voice_action_retry
    }
)

private const val PHASE_MS = 1_600
private const val TITLE_SP = 17
private const val HINT_SP = 13
private const val BUTTON_SP = 15
private const val ORB_DP = 132
private const val CORE_DP = 34
private const val CORE_FRACTION = 0.26f
private const val ICON_DP = 30
private const val SPINNER_DP = 4
private const val SPINNER_GAP_DP = 8
private const val SPINNER_SWEEP = 100f
private const val TARGET_DP = 48
private const val PILL_MAX_CORNER_DP = 24f
private const val PRIVATE_WASH = 0.12f
private const val RING_BASE = 0.12f
private const val RING_LEVEL = 0.62f
private const val RING_BREATH = 0.1f
private const val RING_ALPHA = 0.22f
private const val RING_ALPHA_LOUD = 0.3f
private const val RING_ALPHA_INNER = 0.18f
private const val INNER_RING = 0.55f
private const val HALF = 0.5f
private const val TWO_PI = (2.0 * Math.PI).toFloat()
private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
