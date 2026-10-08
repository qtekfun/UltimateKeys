// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatekeys.LabeledSlider
import com.qtekfun.ultimatekeys.LabeledSwitch
import com.qtekfun.ultimatekeys.MinTouchTarget
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.style.ArgbColor

/** A collapsible group of controls; the whole header is the button and says whether it is open. */
@Composable
fun StyleSection(title: Int, initiallyOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    val state = stringResource(if (open) R.string.a11y_expanded else R.string.a11y_collapsed)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget)
                    .clickable(role = Role.Button) { open = !open }
                    .semantics {
                        heading()
                        stateDescription = state
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                // The sign repeats what the state description says: a screen reader skips it.
                Text(if (open) "−" else "+", Modifier.clearAndSetSemantics { })
            }
            if (open) content()
        }
    }
}

@Composable
fun FloatSliderRow(
    label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    LabeledSlider(stringResource(label), "%.1f".format(value), value, range, onChange)
}

@Composable
fun IntSliderRow(label: Int, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    LabeledSlider(
        label = stringResource(label),
        valueText = value.toString(),
        value = value.toFloat(),
        range = range.first.toFloat()..range.last.toFloat(),
        onChange = { onChange(it.toInt()) }
    )
}

@Composable
fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    LabeledSwitch(stringResource(label), checked, onChange)
}

/** One option of a [ChoiceRow]: the value and the text people read. */
data class Choice<T>(val value: T, val label: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(label: Int, choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                FilterChip(
                    selected = choice.value == selected,
                    onClick = { onSelect(choice.value) },
                    label = { Text(choice.label) }
                )
            }
        }
    }
}

/**
 * A colour with a swatch and a `#RRGGBB` / `#AARRGGBB` field that only commits valid values. The
 * swatch is decorative (the hex code says the same); an invalid code is flagged in words too.
 */
@Composable
fun ColorRow(label: Int, color: ArgbColor, onChange: (ArgbColor) -> Unit) {
    var text by remember(color) { mutableStateOf(color.toHex()) }
    val parsed = ArgbColor.parse(text)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp)
                .background(Color(color.argb), RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clearAndSetSemantics { }
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                ArgbColor.parse(it)?.let(onChange)
            },
            label = { Text(stringResource(label)) },
            isError = parsed == null,
            supportingText = if (parsed == null) {
                { Text(stringResource(R.string.color_invalid)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}
