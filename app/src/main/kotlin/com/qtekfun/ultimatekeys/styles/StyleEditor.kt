// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.styles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.ime.surface.KeyboardPreview
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
import com.qtekfun.ultimatekeys.style.Palette
import com.qtekfun.ultimatekeys.style.PanelTransition
import com.qtekfun.ultimatekeys.style.PopupKind
import com.qtekfun.ultimatekeys.style.PressAnimation
import com.qtekfun.ultimatekeys.style.ShadowKind
import com.qtekfun.ultimatekeys.style.Style
import com.qtekfun.ultimatekeys.style.SuggestionBarStyle
import com.qtekfun.ultimatekeys.style.ToolIcons

/**
 * Edits [initial] with a live preview on top. [onSave] gets the edited style; the editor never
 * writes anything itself, so cancelling is just leaving.
 */
@Composable
fun StyleEditor(
    initial: Style,
    onSave: (Style) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var previewDark by rememberSaveable { mutableStateOf(false) }
    val warnings = remember(draft) { ContrastCheck.warnings(draft) }

    Column(modifier.fillMaxSize()) {
        KeyboardPreview(draft, previewDark)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { previewDark = false }, enabled = previewDark) {
                Text(stringResource(R.string.style_preview_light))
            }
            OutlinedButton(onClick = { previewDark = true }, enabled = !previewDark) {
                Text(stringResource(R.string.style_preview_dark))
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it.take(Style.MAX_NAME)) },
                label = { Text(stringResource(R.string.style_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (warnings.isNotEmpty()) {
                Text(
                    stringResource(R.string.style_contrast_warning),
                    color = MaterialTheme.colorScheme.error
                )
            }
            AppearanceControls(draft) { draft = it }
            KeyControls(draft) { draft = it }
            LabelControls(draft) { draft = it }
            BackgroundControls(draft) { draft = it }
            FeedbackControls(draft) { draft = it }
            BarControls(draft) { draft = it }
            BottomRowControls(draft) { draft = it }
            PanelAndMotionControls(draft) { draft = it }
            PaletteControls(R.string.group_colors_light, draft.light) {
                draft =
                    draft.copy(light = it)
            }
            PaletteControls(R.string.group_colors_dark, draft.dark) {
                draft = draft.copy(dark = it)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.style_back)) }
            OutlinedButton(onClick = { draft = initial }, enabled = draft != initial) {
                Text(stringResource(R.string.style_reset))
            }
            Button(onClick = { onSave(draft.sanitized()) }) {
                Text(stringResource(R.string.style_save))
            }
        }
    }
}

@Composable
private fun AppearanceControls(style: Style, onChange: (Style) -> Unit) {
    StyleSection(R.string.style_appearance, initiallyOpen = true) {
        ChoiceRow(
            R.string.style_appearance,
            listOf(
                Choice(Appearance.FOLLOW_SYSTEM, stringResource(R.string.appearance_system)),
                Choice(Appearance.LIGHT, stringResource(R.string.appearance_light)),
                Choice(Appearance.DARK, stringResource(R.string.appearance_dark))
            ),
            style.appearance
        ) { onChange(style.copy(appearance = it)) }
        SwitchRow(R.string.style_dynamic_colors, style.dynamicColors) {
            onChange(style.copy(dynamicColors = it))
        }
    }
}

@Composable
private fun KeyControls(style: Style, onChange: (Style) -> Unit) {
    val k = style.keys
    fun set(keys: KeyShape) = onChange(style.copy(keys = keys))
    StyleSection(R.string.group_keys) {
        FloatSliderRow(R.string.ctl_corner, k.cornerRadiusDp, KeyShape.CORNER_RANGE) {
            set(k.copy(cornerRadiusDp = it))
        }
        FloatSliderRow(R.string.ctl_gap_x, k.gapXDp, KeyShape.GAP_RANGE) {
            set(k.copy(gapXDp = it))
        }
        FloatSliderRow(R.string.ctl_gap_y, k.gapYDp, KeyShape.GAP_RANGE) {
            set(k.copy(gapYDp = it))
        }
        IntSliderRow(
            R.string.ctl_row_letter,
            k.letterRowHeightPercent,
            KeyShape.ROW_PERCENT_RANGE
        ) {
            set(k.copy(letterRowHeightPercent = it))
        }
        IntSliderRow(
            R.string.ctl_row_number,
            k.numberRowHeightPercent,
            KeyShape.ROW_PERCENT_RANGE
        ) {
            set(k.copy(numberRowHeightPercent = it))
        }
        IntSliderRow(
            R.string.ctl_row_bottom,
            k.bottomRowHeightPercent,
            KeyShape.ROW_PERCENT_RANGE
        ) {
            set(k.copy(bottomRowHeightPercent = it))
        }
        SwitchRow(R.string.ctl_border, k.border) { set(k.copy(border = it)) }
        if (k.border) {
            FloatSliderRow(R.string.ctl_border_width, k.borderWidthDp, KeyShape.BORDER_RANGE) {
                set(k.copy(borderWidthDp = it))
            }
        }
        ChoiceRow(
            R.string.ctl_shadow,
            listOf(
                Choice(ShadowKind.NONE, stringResource(R.string.opt_shadow_none)),
                Choice(ShadowKind.BOTTOM_EDGE, stringResource(R.string.opt_shadow_bottom)),
                Choice(ShadowKind.ELEVATION, stringResource(R.string.opt_shadow_elevation))
            ),
            k.shadow
        ) { set(k.copy(shadow = it)) }
        if (k.shadow != ShadowKind.NONE) {
            FloatSliderRow(R.string.ctl_elevation, k.elevationDp, KeyShape.ELEVATION_RANGE) {
                set(k.copy(elevationDp = it))
            }
        }
    }
}

@Composable
private fun LabelControls(style: Style, onChange: (Style) -> Unit) {
    val l = style.labels
    fun set(labels: LabelStyle) = onChange(style.copy(labels = labels))
    StyleSection(R.string.group_labels) {
        ChoiceRow(
            R.string.ctl_font,
            listOf(
                Choice(FontChoice.SYSTEM, stringResource(R.string.opt_font_system)),
                Choice(FontChoice.INTER, "Inter"),
                Choice(FontChoice.ROBOTO_FLEX, "Roboto Flex"),
                Choice(FontChoice.ATKINSON_HYPERLEGIBLE, "Atkinson Hyperlegible"),
                Choice(FontChoice.NUNITO, "Nunito")
            ),
            l.font
        ) { set(l.copy(font = it)) }
        IntSliderRow(R.string.ctl_weight, l.weight, LabelStyle.WEIGHT_RANGE) {
            set(l.copy(weight = it))
        }
        IntSliderRow(R.string.ctl_label_size, l.sizePercent, LabelStyle.SIZE_RANGE) {
            set(l.copy(sizePercent = it))
        }
        ChoiceRow(
            R.string.ctl_letter_case,
            listOf(
                Choice(LetterCase.FOLLOW_SHIFT, stringResource(R.string.opt_case_shift)),
                Choice(LetterCase.ALWAYS_UPPER, stringResource(R.string.opt_case_upper))
            ),
            l.letterCase
        ) { set(l.copy(letterCase = it)) }
        SwitchRow(R.string.ctl_hints, l.showHints) { set(l.copy(showHints = it)) }
    }
}

@Composable
private fun BackgroundControls(style: Style, onChange: (Style) -> Unit) {
    val b = style.background
    fun set(background: BackgroundStyle) = onChange(style.copy(background = background.sanitized()))
    StyleSection(R.string.group_background) {
        ChoiceRow(
            R.string.ctl_bg_kind,
            listOf(
                Choice(BackgroundKind.SOLID, stringResource(R.string.opt_bg_solid)),
                Choice(BackgroundKind.GRADIENT, stringResource(R.string.opt_bg_gradient))
            ),
            b.kind
        ) { set(b.copy(kind = it)) }
        ColorRow(R.string.ctl_bg_light, b.lightColors.first()) {
            set(b.copy(lightColors = listOf(it) + b.lightColors.drop(1)))
        }
        ColorRow(R.string.ctl_bg_dark, b.darkColors.first()) {
            set(b.copy(darkColors = listOf(it) + b.darkColors.drop(1)))
        }
        if (b.kind == BackgroundKind.GRADIENT) {
            ColorRow(R.string.ctl_bg_light_2, b.lightColors.getOrElse(1) { b.lightColors.last() }) {
                set(b.copy(lightColors = listOf(b.lightColors.first(), it)))
            }
            ColorRow(R.string.ctl_bg_dark_2, b.darkColors.getOrElse(1) { b.darkColors.last() }) {
                set(b.copy(darkColors = listOf(b.darkColors.first(), it)))
            }
            IntSliderRow(R.string.ctl_bg_angle, b.gradientAngleDegrees, 0..GRADIENT_MAX_ANGLE) {
                set(b.copy(gradientAngleDegrees = it))
            }
        }
    }
}

@Composable
private fun FeedbackControls(style: Style, onChange: (Style) -> Unit) {
    val f = style.feedback
    StyleSection(R.string.group_feedback) {
        ChoiceRow(
            R.string.ctl_popup,
            listOf(
                Choice(PopupKind.BUBBLE, stringResource(R.string.opt_popup_bubble)),
                Choice(PopupKind.ENLARGED_KEY, stringResource(R.string.opt_popup_enlarged)),
                Choice(PopupKind.NONE, stringResource(R.string.opt_popup_none))
            ),
            f.popup
        ) { onChange(style.copy(feedback = f.copy(popup = it))) }
        ChoiceRow(
            R.string.ctl_press,
            listOf(
                Choice(PressAnimation.NONE, stringResource(R.string.opt_press_none)),
                Choice(PressAnimation.SCALE, stringResource(R.string.opt_press_scale)),
                Choice(PressAnimation.FADE, stringResource(R.string.opt_press_fade))
            ),
            f.pressAnimation
        ) { onChange(style.copy(feedback = f.copy(pressAnimation = it))) }
    }
}

@Composable
private fun BarControls(style: Style, onChange: (Style) -> Unit) {
    val s = style.suggestionBar
    fun set(bar: SuggestionBarStyle) = onChange(style.copy(suggestionBar = bar))
    StyleSection(R.string.group_suggestions) {
        ChoiceRow(
            R.string.ctl_bar_layout,
            listOf(
                Choice(BarLayout.THREE_WITH_DIVIDERS, stringResource(R.string.opt_bar_three)),
                Choice(BarLayout.SCROLLING_LIST, stringResource(R.string.opt_bar_list))
            ),
            s.layout
        ) { set(s.copy(layout = it)) }
        ChoiceRow(
            R.string.ctl_tool_icons,
            listOf(
                Choice(ToolIcons.SHOWN, stringResource(R.string.opt_tools_shown)),
                Choice(ToolIcons.COLLAPSED, stringResource(R.string.opt_tools_collapsed)),
                Choice(ToolIcons.HIDDEN, stringResource(R.string.opt_tools_hidden))
            ),
            s.toolIcons
        ) { set(s.copy(toolIcons = it)) }
        IntSliderRow(R.string.ctl_bar_height, s.heightDp, SuggestionBarStyle.HEIGHT_RANGE) {
            set(s.copy(heightDp = it))
        }
        IntSliderRow(R.string.ctl_bar_text_size, s.textSizePercent, LabelStyle.SIZE_RANGE) {
            set(s.copy(textSizePercent = it))
        }
        IntSliderRow(R.string.ctl_bar_text_weight, s.textWeight, LabelStyle.WEIGHT_RANGE) {
            set(s.copy(textWeight = it))
        }
    }
}

@Composable
private fun BottomRowControls(style: Style, onChange: (Style) -> Unit) {
    val r = style.bottomRow
    StyleSection(R.string.group_bottom_row) {
        ChoiceRow(
            R.string.ctl_arrangement,
            listOf(
                Choice(
                    BottomRowArrangement.SYMBOLS_GLOBE_COMMA_SPACE_PERIOD_ENTER,
                    stringResource(R.string.opt_row_classic)
                ),
                Choice(
                    BottomRowArrangement.SYMBOLS_EMOJI_SPACE_PERIOD_ENTER,
                    stringResource(R.string.opt_row_emoji)
                ),
                Choice(
                    BottomRowArrangement.SYMBOLS_COMMA_SPACE_MIC_ENTER,
                    stringResource(R.string.opt_row_mic)
                ),
                Choice(
                    BottomRowArrangement.SYMBOLS_GLOBE_SPACE_ENTER,
                    stringResource(R.string.opt_row_globe)
                )
            ),
            r.arrangement
        ) { onChange(style.copy(bottomRow = r.copy(arrangement = it))) }
        ChoiceRow(
            R.string.ctl_mic,
            listOf(
                Choice(MicPlacement.BOTTOM_ROW, stringResource(R.string.opt_mic_row)),
                Choice(MicPlacement.SUGGESTION_BAR, stringResource(R.string.opt_mic_bar))
            ),
            r.micPlacement
        ) { onChange(style.copy(bottomRow = r.copy(micPlacement = it))) }
    }
}

@Composable
private fun PanelAndMotionControls(style: Style, onChange: (Style) -> Unit) {
    StyleSection(R.string.group_panels) {
        FloatSliderRow(
            R.string.ctl_panel_corner,
            style.panels.cornerRadiusDp,
            com.qtekfun.ultimatekeys.style.PanelStyle.CORNER_RANGE
        ) { onChange(style.copy(panels = style.panels.copy(cornerRadiusDp = it))) }
    }
    StyleSection(R.string.group_motion) {
        ChoiceRow(
            R.string.ctl_transition,
            listOf(
                Choice(PanelTransition.NONE, stringResource(R.string.opt_motion_none)),
                Choice(PanelTransition.FADE, stringResource(R.string.opt_motion_fade)),
                Choice(PanelTransition.SLIDE, stringResource(R.string.opt_motion_slide))
            ),
            style.motion.transition
        ) { onChange(style.copy(motion = style.motion.copy(transition = it))) }
        IntSliderRow(
            R.string.ctl_duration,
            style.motion.durationMs,
            com.qtekfun.ultimatekeys.style.MotionStyle.DURATION_RANGE
        ) { onChange(style.copy(motion = style.motion.copy(durationMs = it))) }
    }
}

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
    PaletteField(R.string.col_private, { it.privateTint }, { p, c -> p.copy(privateTint = c) })
)

@Composable
private fun PaletteControls(title: Int, palette: Palette, onChange: (Palette) -> Unit) {
    StyleSection(title) {
        paletteFields.forEach { field ->
            ColorRow(field.label, field.get(palette)) { onChange(field.set(palette, it)) }
        }
    }
}

private const val GRADIENT_MAX_ANGLE = 359
