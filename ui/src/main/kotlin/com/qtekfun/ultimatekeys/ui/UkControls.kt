// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** The switch drawn at the end of a [UkSwitchRow]; the row carries the semantics, not this. */
@Composable
fun UkSwitch(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = UkTheme.colors
    val travel = UkSize.switchWidth - UkSize.switchThumb - UkSize.switchPadding * 2
    val offset by animateDpAsState(if (checked) travel else 0.dp, label = "switch-thumb")
    val track by animateColorAsState(
        if (checked) colors.tint else colors.track.copy(alpha = TRACK_OFF_ALPHA),
        label = "switch-track"
    )
    Box(
        modifier
            .size(UkSize.switchWidth, UkSize.switchHeight)
            .clip(CircleShape)
            .background(track.copy(alpha = if (enabled) 1f else DISABLED_ALPHA))
            .padding(UkSize.switchPadding)
    ) {
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .size(UkSize.switchThumb)
                .background(if (checked) colors.onTint else Color.White, CircleShape)
        )
    }
}

private const val TRACK_OFF_ALPHA = 0.45f

/**
 * A slider row: the [title] with the value in its own unit ([valueText]) at the end, the slider
 * under it. One focus stop for a screen reader that reads the label and the value in its unit (not
 * a percentage of the range) and offers the adjust actions.
 */
@Composable
fun UkSliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val colors = UkTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = UkSpacing.md)
            .padding(top = UkSpacing.md)
            .semantics(mergeDescendants = true) { stateDescription = valueText }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = UkTheme.typography.body, color = colors.label)
            Text(
                valueText,
                Modifier.padding(start = UkSpacing.sm),
                style = UkTheme.typography.body,
                color = colors.secondaryLabel,
                textAlign = TextAlign.End
            )
        }
        if (subtitle != null) {
            Text(subtitle, style = UkTheme.typography.footnote, color = colors.secondaryLabel)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().heightIn(min = UkSize.minTouch),
            colors = SliderDefaults.colors(
                thumbColor = colors.tint,
                activeTrackColor = colors.tint,
                inactiveTrackColor = colors.sliderInactive
            )
        )
    }
}

/** One option of a choice control: the value and the text people read. */
data class UkOption<T>(val value: T, val label: String)

/**
 * Side-by-side options in one rounded track; the selected one is lifted and bold, so it never
 * relies on colour alone. Each segment is a radio button of one selectable group.
 */
@Composable
fun <T> UkSegmented(
    options: List<UkOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = UkTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .selectableGroup()
            .clip(RoundedCornerShape(UkRadius.field))
            .background(colors.segmentTrack)
            .padding(UkSize.segmentTrackPadding),
        horizontalArrangement = Arrangement.spacedBy(UkSize.segmentTrackPadding)
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = UkSize.segmentHeight)
                    .clip(RoundedCornerShape(UkRadius.segment))
                    .background(if (isSelected) colors.segmentSelected else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.value) }
                    )
                    .padding(horizontal = UkSpacing.xs, vertical = UkSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option.label,
                    style = UkTheme.typography.body,
                    color = colors.label,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** A title with a [UkSegmented] control under it, as a row. */
@Composable
fun <T> UkSegmentedRow(
    title: String,
    options: List<UkOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(UkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(UkSpacing.sm)
    ) {
        Text(title, style = UkTheme.typography.body, color = UkTheme.colors.label)
        UkSegmented(options, selected, onSelect)
    }
}

/**
 * A row showing the current option at its end; tapping it opens a menu of all the options, the
 * current one marked. For lists too long or too wordy for [UkSegmentedRow].
 */
@Composable
fun <T> UkPickerRow(
    title: String,
    options: List<UkOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }?.label.orEmpty()
    Box(modifier) {
        UkRow(
            title = title,
            onClick = { open = true },
            role = Role.DropdownList,
            trailing = {
                UkValueText(current)
                UkChevron(pointingDown = true)
            }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                val isSelected = option.value == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else null
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(option.value)
                    },
                    modifier = Modifier.semantics { this.selected = isSelected },
                    trailingIcon = if (isSelected) {
                        { UkIcon(UkGlyph.Check, UkTheme.colors.tint, size = UkSize.chevron) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/** Picks the segmented or the picker presentation for [options] ([ChoicePresentation.of]). */
@Composable
fun <T> UkChoiceRow(
    title: String,
    options: List<UkOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    when (ChoicePresentation.of(options.map { it.label })) {
        ChoicePresentation.Segmented -> UkSegmentedRow(title, options, selected, onSelect, modifier)
        ChoicePresentation.Picker -> UkPickerRow(title, options, selected, onSelect, modifier)
    }
}

/** A text field that sits inside a group row: no container of its own, just the field. */
@Composable
fun UkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    supportingText: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = UkSize.rowMinHeight),
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = trailing,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = UkTheme.colors.destructive
        )
    )
}
