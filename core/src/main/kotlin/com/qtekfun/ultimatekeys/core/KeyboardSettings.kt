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
    val letterLayoutId: String = "es_qwerty",
    /** A manually switched-on private mode ends when the keyboard closes (otherwise: until turned off). */
    val privateModeEndsOnClose: Boolean = true,
    /** Keep a history of copied text (never in private mode, never for sensitive clips). */
    val clipboardEnabled: Boolean = true,
    /** Id of the retention choice (`hour`, `day`, `week`, `forever`); see the clipboard module. */
    val clipboardRetention: String = DEFAULT_CLIPBOARD_RETENTION,
    val clipboardMaxItems: Int = DEFAULT_CLIPBOARD_MAX_ITEMS,
    /** Preferred skin tone of the emoji panel: 0 is none, 1..5 light to dark. */
    val emojiSkinTone: Int = 0,
    /** Recently used emoji, newest first, separated by spaces. */
    val emojiRecents: String = ""
) {
    fun sanitized(): KeyboardSettings = copy(
        heightPercent = heightPercent.coerceIn(HEIGHT_RANGE),
        bottomMarginDp = bottomMarginDp.coerceIn(BOTTOM_MARGIN_RANGE),
        longPressDelayMs = longPressDelayMs.coerceIn(LONG_PRESS_RANGE),
        hapticIntensity = hapticIntensity.coerceIn(PERCENT_RANGE),
        soundVolume = soundVolume.coerceIn(PERCENT_RANGE),
        clipboardMaxItems = clipboardMaxItems.coerceIn(CLIPBOARD_ITEMS_RANGE),
        emojiSkinTone = emojiSkinTone.coerceIn(SKIN_TONE_RANGE)
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
        const val DEFAULT_CLIPBOARD_RETENTION = "day"
        const val DEFAULT_CLIPBOARD_MAX_ITEMS = 50
        val CLIPBOARD_ITEMS_RANGE = 5..500
        val SKIN_TONE_RANGE = 0..5
    }
}
