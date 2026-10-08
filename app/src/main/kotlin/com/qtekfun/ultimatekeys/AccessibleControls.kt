// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Smallest touch target Android recommends (48 dp); every control in the app screens meets it. */
val MinTouchTarget = 48.dp

/** A section title that a screen reader can jump to. */
@Composable
fun Heading(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = style)
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier) =
    Heading(text, MaterialTheme.typography.titleMedium, modifier)

/**
 * A slider that reads as one control: the label, the value in its own unit (not a percentage of
 * the range) and the adjust actions, all in a single focus stop.
 */
@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.semantics(mergeDescendants = true) { stateDescription = valueText }) {
        Text("$label: $valueText")
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget)
        )
    }
}

/** A whole-row switch: the label and the switch are one focus stop and one touch target. */
@Composable
fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
