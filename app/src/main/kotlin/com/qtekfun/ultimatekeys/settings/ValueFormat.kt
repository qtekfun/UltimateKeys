// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import java.util.Locale
import kotlin.math.roundToInt

/** The value labels at the end of slider rows: the number in its own unit, never a range fraction. */
object ValueFormat {
    fun percent(value: Int): String = "$value%"

    fun dp(value: Int): String = "$value dp"

    fun dp(value: Float, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f dp", value)

    fun millis(value: Int): String = "$value ms"

    fun degrees(value: Int): String = "$value°"

    /** Milliseconds as seconds with one decimal ("1.5 s"), in the decimal style of [locale]. */
    fun seconds(millis: Int, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f s", millis / MS_PER_SECOND)

    /** The nearest multiple of [step] (a slider drags continuously; the setting moves in steps). */
    fun snap(value: Float, step: Int): Int = (value / step).roundToInt() * step

    private const val MS_PER_SECOND = 1000f
}
