// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

/** User-facing keyboard settings. Values are always clamped through [sanitized]. */
data class KeyboardSettings(
    val heightPercent: Int = DEFAULT_HEIGHT_PERCENT,
    val bottomMarginDp: Int = DEFAULT_BOTTOM_MARGIN_DP,
    val numberRow: Boolean = false,
    val longPressDelayMs: Int = DEFAULT_LONG_PRESS_MS,
    val hapticIntensity: Int = DEFAULT_HAPTIC,
    val soundVolume: Int = 0,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val smartPunctuation: Boolean = true,
    val showSuggestions: Boolean = true,
    val autoCorrect: Boolean = true,
    val letterLayoutId: String = "es_qwerty"
) {
    fun sanitized(): KeyboardSettings = copy(
        heightPercent = heightPercent.coerceIn(HEIGHT_RANGE),
        bottomMarginDp = bottomMarginDp.coerceIn(BOTTOM_MARGIN_RANGE),
        longPressDelayMs = longPressDelayMs.coerceIn(LONG_PRESS_RANGE),
        hapticIntensity = hapticIntensity.coerceIn(PERCENT_RANGE),
        soundVolume = soundVolume.coerceIn(PERCENT_RANGE)
    )

    companion object {
        const val DEFAULT_HEIGHT_PERCENT = 100
        const val DEFAULT_BOTTOM_MARGIN_DP = 12
        const val DEFAULT_LONG_PRESS_MS = 350
        const val DEFAULT_HAPTIC = 50
        val HEIGHT_RANGE = 70..130
        val BOTTOM_MARGIN_RANGE = 0..48
        val LONG_PRESS_RANGE = 150..800
        val PERCENT_RANGE = 0..100
    }
}
