// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import com.qtekfun.ultimatekeys.languages.Language
import com.qtekfun.ultimatekeys.languages.LanguageCatalog

/** User-facing keyboard settings. Values are always clamped through [sanitized]. */
data class KeyboardSettings(
    val heightPercent: Int = DEFAULT_HEIGHT_PERCENT,
    val bottomMarginDp: Int = DEFAULT_BOTTOM_MARGIN_DP,
    /** Space kept free at each side of the keys, away from the screen edge and its gestures. */
    val sideMarginDp: Int = DEFAULT_SIDE_MARGIN_DP,
    /** How much wider (in percent) the letters at the end of each row are, to make them easier to hit. */
    val edgeKeyBoostPercent: Int = 0,
    val numberRow: Boolean = false,
    val longPressDelayMs: Int = DEFAULT_LONG_PRESS_MS,
    val hapticIntensity: Int = DEFAULT_HAPTIC,
    val soundVolume: Int = 0,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val smartPunctuation: Boolean = true,
    val showSuggestions: Boolean = true,
    val autoCorrect: Boolean = true,
    /** The letter layout in use; it belongs to one of the [enabledLanguages] (see [sanitized]). */
    val letterLayoutId: String = "es_qwerty",
    /**
     * The languages typed and suggested at once, as catalog tags, in the order the language key cycles through
     * them. At least one and at most [LanguageCatalog.MAX_ENABLED]; Spanish and English by default.
     */
    val enabledLanguages: List<String> = LanguageCatalog.DEFAULT_ENABLED,
    /** The layout chosen for a language that has several, by tag; absent means the language default. */
    val languageLayouts: Map<String, String> = emptyMap(),
    /** Draw the name of the active language on the space bar (otherwise the word "space"). */
    val showLanguageOnSpace: Boolean = true,
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
    val emojiRecents: String = "",
    /** Gesture typing: glide over the letters to type a word. */
    val gestureTyping: Boolean = true,
    /** Draw the line the finger leaves while gliding. */
    val gestureTrail: Boolean = true,
    /** 0 = a long glide is needed to start a gesture, 100 = a short one is enough. */
    val gestureSensitivity: Int = DEFAULT_GESTURE_SENSITIVITY,
    /** Show the dictation microphone; off hides it everywhere and ignores the key. */
    val dictationEnabled: Boolean = true,
    /** Dictation language: `auto` (Spanish or English per utterance), `es` or `en`. */
    val dictationLanguage: String = DICTATION_AUTO,
    /** Side of the margin under the keys where the microphone sits: `left`, `center` or `right`. */
    val dictationMicSide: String = MIC_LEFT,
    /** Silence after speech that ends a dictation, in milliseconds. */
    val dictationSilenceMs: Int = DEFAULT_DICTATION_SILENCE_MS,
    /** Id of the model chosen for dictation; empty means the first installed one. */
    val dictationModelId: String = "",
    /** Download models only on an unmetered connection. */
    val modelDownloadWifiOnly: Boolean = true
) {
    /** The language of the layout in use. */
    val activeLanguage: Language
        get() = LanguageCatalog.languageOfLayout(letterLayoutId, enabledLanguages)
            ?: LanguageCatalog.all.first()

    /** The layout the language key switches to when it moves to [tag]. */
    fun layoutOf(tag: String): String = LanguageCatalog.layoutFor(tag, languageLayouts)

    /** The enabled language after the active one, wrapping around; the active one when it is alone. */
    fun nextLanguage(): String {
        val index = enabledLanguages.indexOf(activeLanguage.tag)
        return enabledLanguages[(index + 1).mod(enabledLanguages.size)]
    }

    fun sanitized(): KeyboardSettings = sanitizedLanguages().copy(
        heightPercent = heightPercent.coerceIn(HEIGHT_RANGE),
        bottomMarginDp = bottomMarginDp.coerceIn(BOTTOM_MARGIN_RANGE),
        sideMarginDp = sideMarginDp.coerceIn(SIDE_MARGIN_RANGE),
        edgeKeyBoostPercent = edgeKeyBoostPercent.coerceIn(EDGE_BOOST_RANGE),
        longPressDelayMs = longPressDelayMs.coerceIn(LONG_PRESS_RANGE),
        hapticIntensity = hapticIntensity.coerceIn(PERCENT_RANGE),
        soundVolume = soundVolume.coerceIn(PERCENT_RANGE),
        clipboardMaxItems = clipboardMaxItems.coerceIn(CLIPBOARD_ITEMS_RANGE),
        emojiSkinTone = emojiSkinTone.coerceIn(SKIN_TONE_RANGE),
        gestureSensitivity = gestureSensitivity.coerceIn(PERCENT_RANGE),
        dictationLanguage = dictationLanguage.takeIf {
            it in DICTATION_LANGUAGES
        } ?: DICTATION_AUTO,
        dictationMicSide = dictationMicSide.takeIf { it in MIC_SIDES } ?: MIC_LEFT,
        dictationSilenceMs = dictationSilenceMs.coerceIn(DICTATION_SILENCE_RANGE)
    )

    /** Makes the language settings consistent with each other and with the catalog. */
    private fun sanitizedLanguages(): KeyboardSettings {
        val enabled = LanguageCatalog.sanitizeEnabled(enabledLanguages)
        val layouts = LanguageCatalog.sanitizeLayouts(languageLayouts, enabled)
        val owner = LanguageCatalog.languageOfLayout(letterLayoutId, enabled)
        val layout = if (owner != null && owner.tag in enabled &&
            letterLayoutId in owner.layoutIds
        ) {
            letterLayoutId
        } else {
            LanguageCatalog.layoutFor(enabled.first(), layouts)
        }
        return copy(enabledLanguages = enabled, languageLayouts = layouts, letterLayoutId = layout)
    }

    companion object {
        const val DEFAULT_HEIGHT_PERCENT = 100
        const val DEFAULT_BOTTOM_MARGIN_DP = 12
        const val DEFAULT_SIDE_MARGIN_DP = 4
        const val DEFAULT_LONG_PRESS_MS = 350
        const val DEFAULT_HAPTIC = 50
        const val DEFAULT_GESTURE_SENSITIVITY = 50
        val HEIGHT_RANGE = 70..130
        val BOTTOM_MARGIN_RANGE = 0..48
        val SIDE_MARGIN_RANGE = 0..32
        val EDGE_BOOST_RANGE = 0..40
        val LONG_PRESS_RANGE = 150..800
        val PERCENT_RANGE = 0..100
        const val DEFAULT_CLIPBOARD_RETENTION = "day"
        const val DEFAULT_CLIPBOARD_MAX_ITEMS = 50
        val CLIPBOARD_ITEMS_RANGE = 5..500
        val SKIN_TONE_RANGE = 0..5
        const val MIC_LEFT = "left"
        const val MIC_CENTER = "center"
        const val MIC_RIGHT = "right"
        val MIC_SIDES = listOf(MIC_LEFT, MIC_CENTER, MIC_RIGHT)
        const val DICTATION_AUTO = "auto"
        val DICTATION_LANGUAGES = listOf(DICTATION_AUTO, "es", "en")
        const val DEFAULT_DICTATION_SILENCE_MS = 1500
        val DICTATION_SILENCE_RANGE = 500..5000
    }
}
