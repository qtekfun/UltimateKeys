// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("TooManyFunctions")

package com.qtekfun.ultimatekeys.styles

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.ime.surface.KeyboardPreview
import com.qtekfun.ultimatekeys.ime.surface.PreviewContent
import com.qtekfun.ultimatekeys.settings.ValueFormat
import com.qtekfun.ultimatekeys.settings.choiceOf
import com.qtekfun.ultimatekeys.settings.floatSlider
import com.qtekfun.ultimatekeys.settings.intSlider
import com.qtekfun.ultimatekeys.settings.opt
import com.qtekfun.ultimatekeys.settings.rememberRouteStack
import com.qtekfun.ultimatekeys.settings.toggle
import com.qtekfun.ultimatekeys.style.Appearance
import com.qtekfun.ultimatekeys.style.ArgbColor
import com.qtekfun.ultimatekeys.style.BackgroundKind
import com.qtekfun.ultimatekeys.style.BackgroundStyle
import com.qtekfun.ultimatekeys.style.BarLayout
import com.qtekfun.ultimatekeys.style.BottomRowArrangement
import com.qtekfun.ultimatekeys.style.ContrastCheck
import com.qtekfun.ultimatekeys.style.FontChoice
import com.qtekfun.ultimatekeys.style.KeyShape
import com.qtekfun.ultimatekeys.style.LabelStyle
import com.qtekfun.ultimatekeys.style.LetterCase
import com.qtekfun.ultimatekeys.style.MicPlacement
import com.qtekfun.ultimatekeys.style.MotionStyle
import com.qtekfun.ultimatekeys.style.Palette
import com.qtekfun.ultimatekeys.style.PanelStyle
import com.qtekfun.ultimatekeys.style.PanelTransition
import com.qtekfun.ultimatekeys.style.PopupKind
import com.qtekfun.ultimatekeys.style.PressAnimation
import com.qtekfun.ultimatekeys.style.ShadowKind
import com.qtekfun.ultimatekeys.style.Style
import com.qtekfun.ultimatekeys.style.SuggestionBarStyle
import com.qtekfun.ultimatekeys.style.ToolIcons
import com.qtekfun.ultimatekeys.ui.ScreenInsets
import com.qtekfun.ultimatekeys.ui.UkActionRow
import com.qtekfun.ultimatekeys.ui.UkBarButton
import com.qtekfun.ultimatekeys.ui.UkGroupScope
import com.qtekfun.ultimatekeys.ui.UkNavRow
import com.qtekfun.ultimatekeys.ui.UkOption
import com.qtekfun.ultimatekeys.ui.UkScreen
import com.qtekfun.ultimatekeys.ui.UkSegmented
import com.qtekfun.ultimatekeys.ui.UkSpacing
import com.qtekfun.ultimatekeys.ui.UkTextField
import com.qtekfun.ultimatekeys.ui.UkTheme
import com.qtekfun.ultimatekeys.ui.group

private val LOOK_SECTIONS = listOf(
    EditorSection.Appearance,
    EditorSection.Keys,
    EditorSection.Labels,
    EditorSection.Background,
    EditorSection.Feedback,
    EditorSection.SuggestionBar,
    EditorSection.BottomRow,
    EditorSection.Panels,
    EditorSection.Motion
)

private val COLOR_SECTIONS = listOf(EditorSection.ColorsLight, EditorSection.ColorsDark)

/** Keeps the preview a keyboard-like strip, short enough to leave room for the controls. */
private const val EDITOR_PREVIEW_PERCENT = 70

/**
 * Edits [initial] with the live preview pinned under the bar, so every change shows at once
 * whichever group is open. The overview lists the groups; each opens as a screen of its own and
 * back returns to the overview, then leaves. [onSave] gets the edited style; the editor never
 * writes anything itself, so cancelling is just leaving.
 */
@Composable
fun StyleEditor(
    initial: Style,
    onSave: (Style) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = ScreenInsets.current(),
    startAt: EditorSection = EditorSection.Overview
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var previewDark by rememberSaveable { mutableStateOf(false) }
    var stack by rememberRouteStack(
        EditorSection.Overview,
        EditorSection.entries,
        listOf(EditorSection.Overview, startAt).distinct()
    )
    BackHandler(enabled = stack.canGoBack) { stack = stack.back() }
    val section = stack.current
    val warnings = remember(draft) { ContrastCheck.warnings(draft) }
    val back = stringResource(R.string.nav_back)

    UkScreen(
        title = stringResource(section.title),
        modifier = modifier,
        insets = insets,
        onBack = { if (stack.canGoBack) stack = stack.back() else onBack() },
        backText = back,
        backDescription = back,
        actions = {
            UkBarButton(stringResource(R.string.style_save), onClick = {
                onSave(draft.sanitized())
            })
        },
        pinned = { EditorPreview(draft, previewDark) { previewDark = it } },
        listState = key(section) { rememberLazyListState() },
        largeTitle = false
    ) {
        if (section == EditorSection.Overview) {
            overview(
                draft = draft,
                initial = initial,
                warn = warnings.isNotEmpty(),
                onName = { draft = draft.copy(name = it.take(Style.MAX_NAME)) },
                onOpen = { stack = stack.open(it) },
                onReset = { draft = initial }
            )
        } else {
            sectionContent(section, draft) { draft = it }
        }
    }
}

@Composable
private fun EditorPreview(style: Style, dark: Boolean, onDark: (Boolean) -> Unit) {
    val previewLabel = stringResource(R.string.style_preview, style.name)
    val options = listOf(
        UkOption(false, stringResource(R.string.style_preview_light)),
        UkOption(true, stringResource(R.string.style_preview_dark))
    )
    Box(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column {
            Box(Modifier.clearAndSetSemantics { contentDescription = previewLabel }) {
                KeyboardPreview(
                    style,
                    dark,
                    content = PreviewContent(heightPercent = EDITOR_PREVIEW_PERCENT)
                )
            }
            UkSegmented(
                options,
                dark,
                onDark,
                Modifier.padding(horizontal = UkSpacing.md, vertical = UkSpacing.sm)
            )
        }
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.overview(
    draft: Style,
    initial: Style,
    warn: Boolean,
    onName: (String) -> Unit,
    onOpen: (EditorSection) -> Unit,
    onReset: () -> Unit
) {
    group(key = "name") {
        row {
            UkTextField(
                value = draft.name,
                onValueChange = onName,
                label = stringResource(R.string.style_name),
                singleLine = true
            )
        }
    }
    if (warn) {
        item(key = "contrast") {
            Text(
                stringResource(R.string.style_contrast_warning),
                modifier = Modifier
                    .padding(horizontal = UkSpacing.lg, vertical = UkSpacing.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = UkTheme.typography.footnote,
                color = UkTheme.colors.destructive
            )
        }
    }
    group(key = "look", header = null) { sectionRows(LOOK_SECTIONS, onOpen) }
    group(key = "colors", header = null) { sectionRows(COLOR_SECTIONS, onOpen) }
    group(key = "reset") {
        row {
            UkActionRow(
                stringResource(R.string.style_reset),
                onClick = onReset,
                destructive = true,
                enabled = draft != initial
            )
        }
    }
}

private fun UkGroupScope.sectionRows(
    sections: List<EditorSection>,
    onOpen: (EditorSection) -> Unit
) {
    sections.forEach { section ->
        row { UkNavRow(stringResource(section.title), onClick = { onOpen(section) }) }
    }
}

private fun LazyListScope.sectionContent(
    section: EditorSection,
    style: Style,
    onChange: (Style) -> Unit
) {
    when (section) {
        EditorSection.Overview -> Unit

        EditorSection.Appearance -> group { appearanceRows(style, onChange) }

        EditorSection.Keys -> group { keyRows(style, onChange) }

        EditorSection.Labels -> group { labelRows(style, onChange) }

        EditorSection.Background -> group { backgroundRows(style, onChange) }

        EditorSection.Feedback -> group { feedbackRows(style, onChange) }

        EditorSection.SuggestionBar -> group { barRows(style, onChange) }

        EditorSection.BottomRow -> group { bottomRowRows(style, onChange) }

        EditorSection.Panels -> group { panelRows(style, onChange) }

        EditorSection.Motion -> group { motionRows(style, onChange) }

        EditorSection.ColorsLight -> group {
            paletteRows(style.light) { onChange(style.copy(light = it)) }
        }

        EditorSection.ColorsDark -> group {
            paletteRows(style.dark) { onChange(style.copy(dark = it)) }
        }
    }
}

private fun UkGroupScope.appearanceRows(style: Style, onChange: (Style) -> Unit) {
    choiceOf(R.string.style_appearance, style.appearance, {
        onChange(style.copy(appearance = it))
    }) {
        listOf(
            opt(Appearance.FOLLOW_SYSTEM, R.string.appearance_system),
            opt(Appearance.LIGHT, R.string.appearance_light),
            opt(Appearance.DARK, R.string.appearance_dark)
        )
    }
    toggle(R.string.style_dynamic_colors, style.dynamicColors) {
        onChange(style.copy(dynamicColors = it))
    }
}

@Suppress("LongMethod")
private fun UkGroupScope.keyRows(style: Style, onChange: (Style) -> Unit) {
    val k = style.keys
    fun set(keys: KeyShape) = onChange(style.copy(keys = keys))
    floatSlider(R.string.ctl_corner, k.cornerRadiusDp, KeyShape.CORNER_RANGE, ValueFormat::dp) {
        set(k.copy(cornerRadiusDp = it))
    }
    floatSlider(R.string.ctl_gap_x, k.gapXDp, KeyShape.GAP_RANGE, ValueFormat::dp) {
        set(k.copy(gapXDp = it))
    }
    floatSlider(R.string.ctl_gap_y, k.gapYDp, KeyShape.GAP_RANGE, ValueFormat::dp) {
        set(k.copy(gapYDp = it))
    }
    intSlider(
        R.string.ctl_row_letter,
        k.letterRowHeightPercent,
        KeyShape.ROW_PERCENT_RANGE,
        ValueFormat::percent
    ) {
        set(k.copy(letterRowHeightPercent = it))
    }
    intSlider(
        R.string.ctl_row_number,
        k.numberRowHeightPercent,
        KeyShape.ROW_PERCENT_RANGE,
        ValueFormat::percent
    ) {
        set(k.copy(numberRowHeightPercent = it))
    }
    intSlider(
        R.string.ctl_row_bottom,
        k.bottomRowHeightPercent,
        KeyShape.ROW_PERCENT_RANGE,
        ValueFormat::percent
    ) {
        set(k.copy(bottomRowHeightPercent = it))
    }
    toggle(R.string.ctl_border, k.border) { set(k.copy(border = it)) }
    if (k.border) {
        floatSlider(
            R.string.ctl_border_width,
            k.borderWidthDp,
            KeyShape.BORDER_RANGE,
            ValueFormat::dp
        ) {
            set(k.copy(borderWidthDp = it))
        }
    }
    choiceOf(R.string.ctl_shadow, k.shadow, { set(k.copy(shadow = it)) }) {
        listOf(
            opt(ShadowKind.NONE, R.string.opt_shadow_none),
            opt(ShadowKind.BOTTOM_EDGE, R.string.opt_shadow_bottom),
            opt(ShadowKind.ELEVATION, R.string.opt_shadow_elevation)
        )
    }
    if (k.shadow != ShadowKind.NONE) {
        floatSlider(
            R.string.ctl_elevation,
            k.elevationDp,
            KeyShape.ELEVATION_RANGE,
            ValueFormat::dp
        ) {
            set(k.copy(elevationDp = it))
        }
    }
}

private fun UkGroupScope.labelRows(style: Style, onChange: (Style) -> Unit) {
    val l = style.labels
    fun set(labels: LabelStyle) = onChange(style.copy(labels = labels))
    choiceOf(R.string.ctl_font, l.font, { set(l.copy(font = it)) }) {
        listOf(
            opt(FontChoice.SYSTEM, R.string.opt_font_system),
            UkOption(FontChoice.INTER, "Inter"),
            UkOption(FontChoice.ROBOTO_FLEX, "Roboto Flex"),
            UkOption(FontChoice.ATKINSON_HYPERLEGIBLE, "Atkinson Hyperlegible"),
            UkOption(FontChoice.NUNITO, "Nunito")
        )
    }
    intSlider(R.string.ctl_weight, l.weight, LabelStyle.WEIGHT_RANGE, Int::toString) {
        set(l.copy(weight = it))
    }
    intSlider(R.string.ctl_label_size, l.sizePercent, LabelStyle.SIZE_RANGE, ValueFormat::percent) {
        set(l.copy(sizePercent = it))
    }
    choiceOf(R.string.ctl_letter_case, l.letterCase, { set(l.copy(letterCase = it)) }) {
        listOf(
            opt(LetterCase.FOLLOW_SHIFT, R.string.opt_case_shift),
            opt(LetterCase.ALWAYS_UPPER, R.string.opt_case_upper)
        )
    }
    toggle(R.string.ctl_hints, l.showHints) { set(l.copy(showHints = it)) }
}

private fun UkGroupScope.backgroundRows(style: Style, onChange: (Style) -> Unit) {
    val b = style.background
    fun set(background: BackgroundStyle) = onChange(style.copy(background = background.sanitized()))
    choiceOf(R.string.ctl_bg_kind, b.kind, { set(b.copy(kind = it)) }) {
        listOf(
            opt(BackgroundKind.SOLID, R.string.opt_bg_solid),
            opt(BackgroundKind.GRADIENT, R.string.opt_bg_gradient)
        )
    }
    colorField(R.string.ctl_bg_light, b.lightColors.first()) {
        set(b.copy(lightColors = listOf(it) + b.lightColors.drop(1)))
    }
    colorField(R.string.ctl_bg_dark, b.darkColors.first()) {
        set(b.copy(darkColors = listOf(it) + b.darkColors.drop(1)))
    }
    if (b.kind == BackgroundKind.GRADIENT) {
        colorField(R.string.ctl_bg_light_2, b.lightColors.getOrElse(1) { b.lightColors.last() }) {
            set(b.copy(lightColors = listOf(b.lightColors.first(), it)))
        }
        colorField(R.string.ctl_bg_dark_2, b.darkColors.getOrElse(1) { b.darkColors.last() }) {
            set(b.copy(darkColors = listOf(b.darkColors.first(), it)))
        }
        intSlider(
            R.string.ctl_bg_angle,
            b.gradientAngleDegrees,
            0..GRADIENT_MAX_ANGLE,
            ValueFormat::degrees
        ) {
            set(b.copy(gradientAngleDegrees = it))
        }
    }
}

private fun UkGroupScope.feedbackRows(style: Style, onChange: (Style) -> Unit) {
    val f = style.feedback
    choiceOf(R.string.ctl_popup, f.popup, { onChange(style.copy(feedback = f.copy(popup = it))) }) {
        listOf(
            opt(PopupKind.BUBBLE, R.string.opt_popup_bubble),
            opt(PopupKind.ENLARGED_KEY, R.string.opt_popup_enlarged),
            opt(PopupKind.NONE, R.string.opt_popup_none)
        )
    }
    choiceOf(
        R.string.ctl_press,
        f.pressAnimation,
        { onChange(style.copy(feedback = f.copy(pressAnimation = it))) }
    ) {
        listOf(
            opt(PressAnimation.NONE, R.string.opt_press_none),
            opt(PressAnimation.SCALE, R.string.opt_press_scale),
            opt(PressAnimation.FADE, R.string.opt_press_fade)
        )
    }
}

private fun UkGroupScope.barRows(style: Style, onChange: (Style) -> Unit) {
    val s = style.suggestionBar
    fun set(bar: SuggestionBarStyle) = onChange(style.copy(suggestionBar = bar))
    choiceOf(R.string.ctl_bar_layout, s.layout, { set(s.copy(layout = it)) }) {
        listOf(
            opt(BarLayout.THREE_WITH_DIVIDERS, R.string.opt_bar_three),
            opt(BarLayout.SCROLLING_LIST, R.string.opt_bar_list)
        )
    }
    choiceOf(R.string.ctl_tool_icons, s.toolIcons, { set(s.copy(toolIcons = it)) }) {
        listOf(
            opt(ToolIcons.SHOWN, R.string.opt_tools_shown),
            opt(ToolIcons.COLLAPSED, R.string.opt_tools_collapsed),
            opt(ToolIcons.HIDDEN, R.string.opt_tools_hidden)
        )
    }
    intSlider(
        R.string.ctl_bar_height,
        s.heightDp,
        SuggestionBarStyle.HEIGHT_RANGE,
        ValueFormat::dp
    ) {
        set(s.copy(heightDp = it))
    }
    intSlider(
        R.string.ctl_bar_text_size,
        s.textSizePercent,
        LabelStyle.SIZE_RANGE,
        ValueFormat::percent
    ) {
        set(s.copy(textSizePercent = it))
    }
    intSlider(R.string.ctl_bar_text_weight, s.textWeight, LabelStyle.WEIGHT_RANGE, Int::toString) {
        set(s.copy(textWeight = it))
    }
}

private fun UkGroupScope.bottomRowRows(style: Style, onChange: (Style) -> Unit) {
    val r = style.bottomRow
    choiceOf(R.string.ctl_arrangement, r.arrangement, {
        onChange(style.copy(bottomRow = r.copy(arrangement = it)))
    }) {
        listOf(
            opt(
                BottomRowArrangement.SYMBOLS_GLOBE_COMMA_SPACE_PERIOD_ENTER,
                R.string.opt_row_classic
            ),
            opt(BottomRowArrangement.SYMBOLS_EMOJI_SPACE_PERIOD_ENTER, R.string.opt_row_emoji),
            opt(BottomRowArrangement.SYMBOLS_COMMA_SPACE_MIC_ENTER, R.string.opt_row_mic),
            opt(BottomRowArrangement.SYMBOLS_GLOBE_SPACE_ENTER, R.string.opt_row_globe)
        )
    }
    choiceOf(R.string.ctl_mic, r.micPlacement, {
        onChange(style.copy(bottomRow = r.copy(micPlacement = it)))
    }) {
        listOf(
            opt(MicPlacement.BOTTOM_MARGIN, R.string.opt_mic_margin),
            opt(MicPlacement.BOTTOM_ROW, R.string.opt_mic_row),
            opt(MicPlacement.SUGGESTION_BAR, R.string.opt_mic_bar)
        )
    }
}

private fun UkGroupScope.panelRows(style: Style, onChange: (Style) -> Unit) {
    floatSlider(
        R.string.ctl_panel_corner,
        style.panels.cornerRadiusDp,
        PanelStyle.CORNER_RANGE,
        ValueFormat::dp
    ) {
        onChange(style.copy(panels = style.panels.copy(cornerRadiusDp = it)))
    }
}

private fun UkGroupScope.motionRows(style: Style, onChange: (Style) -> Unit) {
    choiceOf(
        R.string.ctl_transition,
        style.motion.transition,
        { onChange(style.copy(motion = style.motion.copy(transition = it))) }
    ) {
        listOf(
            opt(PanelTransition.NONE, R.string.opt_motion_none),
            opt(PanelTransition.FADE, R.string.opt_motion_fade),
            opt(PanelTransition.SLIDE, R.string.opt_motion_slide)
        )
    }
    intSlider(
        R.string.ctl_duration,
        style.motion.durationMs,
        MotionStyle.DURATION_RANGE,
        ValueFormat::millis
    ) {
        onChange(style.copy(motion = style.motion.copy(durationMs = it)))
    }
}

private fun UkGroupScope.colorField(
    @StringRes label: Int,
    color: ArgbColor,
    onChange: (ArgbColor) -> Unit
) = row { ColorRow(stringResource(label), color, onChange) }

private class PaletteField(
    val label: Int,
    val get: (Palette) -> ArgbColor,
    val set: (Palette, ArgbColor) -> Palette
)

private val paletteFields = listOf(
    PaletteField(R.string.col_key_letter, { it.keyLetter }, { p, c -> p.copy(keyLetter = c) }),
    PaletteField(R.string.col_key_function, {
        it.keyFunction
    }, { p, c -> p.copy(keyFunction = c) }),
    PaletteField(R.string.col_key_space, { it.keySpace }, { p, c -> p.copy(keySpace = c) }),
    PaletteField(R.string.col_key_action, { it.keyAction }, { p, c -> p.copy(keyAction = c) }),
    PaletteField(R.string.col_key_border, { it.keyBorder }, { p, c -> p.copy(keyBorder = c) }),
    PaletteField(R.string.col_key_shadow, { it.keyShadow }, { p, c -> p.copy(keyShadow = c) }),
    PaletteField(R.string.col_label, { it.labelText }, { p, c -> p.copy(labelText = c) }),
    PaletteField(R.string.col_action_label, {
        it.actionLabelText
    }, { p, c -> p.copy(actionLabelText = c) }),
    PaletteField(R.string.col_hint, { it.hintText }, { p, c -> p.copy(hintText = c) }),
    PaletteField(R.string.col_press, { it.pressHighlight }, { p, c -> p.copy(pressHighlight = c) }),
    PaletteField(R.string.col_popup_bg, {
        it.popupBackground
    }, { p, c -> p.copy(popupBackground = c) }),
    PaletteField(R.string.col_popup_text, { it.popupText }, { p, c -> p.copy(popupText = c) }),
    PaletteField(R.string.col_bar_bg, { it.barBackground }, { p, c -> p.copy(barBackground = c) }),
    PaletteField(R.string.col_bar_text, { it.barText }, { p, c -> p.copy(barText = c) }),
    PaletteField(R.string.col_bar_divider, { it.barDivider }, { p, c -> p.copy(barDivider = c) }),
    PaletteField(R.string.col_panel_bg, {
        it.panelBackground
    }, { p, c -> p.copy(panelBackground = c) }),
    PaletteField(R.string.col_panel_surface, {
        it.panelSurface
    }, { p, c -> p.copy(panelSurface = c) }),
    PaletteField(R.string.col_panel_text, { it.panelText }, { p, c -> p.copy(panelText = c) }),
    PaletteField(R.string.col_private, { it.privateTint }, { p, c -> p.copy(privateTint = c) }),
    PaletteField(R.string.col_gesture_trail, {
        it.gestureTrail
    }, { p, c -> p.copy(gestureTrail = c) })
)

private fun UkGroupScope.paletteRows(palette: Palette, onChange: (Palette) -> Unit) {
    paletteFields.forEach { field ->
        colorField(field.label, field.get(palette)) { onChange(field.set(palette, it)) }
    }
}

private const val GRADIENT_MAX_ANGLE = 359
