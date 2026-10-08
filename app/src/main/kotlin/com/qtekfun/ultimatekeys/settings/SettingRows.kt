// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatekeys.ui.UkChoiceRow
import com.qtekfun.ultimatekeys.ui.UkGroupScope
import com.qtekfun.ultimatekeys.ui.UkOption
import com.qtekfun.ultimatekeys.ui.UkSliderRow
import com.qtekfun.ultimatekeys.ui.UkSwitchRow
import kotlin.math.roundToInt

/** Group-builder shortcuts for the common setting rows; titles are string resources. */

internal fun UkGroupScope.toggle(
    @StringRes title: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) = row { UkSwitchRow(stringResource(title), checked, onChange) }

internal fun UkGroupScope.intSlider(
    @StringRes title: Int,
    value: Int,
    range: IntRange,
    format: (Int) -> String,
    step: Int = 1,
    onChange: (Int) -> Unit
) = row { IntSliderRow(stringResource(title), value, range, format, step, onChange) }

internal fun UkGroupScope.floatSlider(
    @StringRes title: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit
) = row {
    UkSliderRow(stringResource(title), format(value), value, range, onChange)
}

internal fun <T> UkGroupScope.choice(
    @StringRes title: Int,
    options: List<UkOption<T>>,
    selected: T,
    onSelect: (T) -> Unit
) = row { UkChoiceRow(stringResource(title), options, selected, onSelect) }

/** An integer slider that moves in [step]s and shows its value through [format]. */
@Composable
internal fun IntSliderRow(
    title: String,
    value: Int,
    range: IntRange,
    format: (Int) -> String,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    UkSliderRow(
        title = title,
        valueText = format(value),
        value = value.toFloat(),
        range = range.first.toFloat()..range.last.toFloat(),
        onChange = { onChange(if (step == 1) it.roundToInt() else ValueFormat.snap(it, step)) }
    )
}

/** A choice whose option labels need string resources: [options] is composed where it is shown. */
internal fun <T> UkGroupScope.choiceOf(
    @StringRes title: Int,
    selected: T,
    onSelect: (T) -> Unit,
    options: @Composable () -> List<UkOption<T>>
) = row { UkChoiceRow(stringResource(title), options(), selected, onSelect) }

/** An option whose label is a string resource. */
@Composable
internal fun <T> opt(value: T, @StringRes label: Int): UkOption<T> =
    UkOption(value, stringResource(label))
