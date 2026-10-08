// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<KeyboardSettings> = store.data.map { it.toSettings() }

    override suspend fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings()).sanitized()) }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun Preferences.toSettings(): KeyboardSettings {
        val d = KeyboardSettings()
        return KeyboardSettings(
            heightPercent = this[HEIGHT] ?: d.heightPercent,
            bottomMarginDp = this[BOTTOM_MARGIN] ?: d.bottomMarginDp,
            sideMarginDp = this[SIDE_MARGIN] ?: d.sideMarginDp,
            edgeKeyBoostPercent = this[EDGE_BOOST] ?: d.edgeKeyBoostPercent,
            numberRow = this[NUMBER_ROW] ?: d.numberRow,
            longPressDelayMs = this[LONG_PRESS] ?: d.longPressDelayMs,
            hapticIntensity = this[HAPTIC] ?: d.hapticIntensity,
            soundVolume = this[SOUND] ?: d.soundVolume,
            autoCapitalize = this[AUTO_CAP] ?: d.autoCapitalize,
            doubleSpacePeriod = this[DOUBLE_SPACE] ?: d.doubleSpacePeriod,
            smartPunctuation = this[SMART_PUNCT] ?: d.smartPunctuation,
            showSuggestions = this[SHOW_SUGGESTIONS] ?: d.showSuggestions,
            autoCorrect = this[AUTO_CORRECT] ?: d.autoCorrect,
            letterLayoutId = this[LAYOUT] ?: d.letterLayoutId,
            privateModeEndsOnClose = this[PRIVATE_ENDS_ON_CLOSE] ?: d.privateModeEndsOnClose,
            clipboardEnabled = this[CLIPBOARD_ENABLED] ?: d.clipboardEnabled,
            clipboardRetention = this[CLIPBOARD_RETENTION] ?: d.clipboardRetention,
            clipboardMaxItems = this[CLIPBOARD_MAX_ITEMS] ?: d.clipboardMaxItems,
            emojiSkinTone = this[EMOJI_SKIN_TONE] ?: d.emojiSkinTone,
            emojiRecents = this[EMOJI_RECENTS] ?: d.emojiRecents
        ).withGesture(this).withDictation(this).sanitized()
    }

    private fun KeyboardSettings.withGesture(prefs: Preferences) = copy(
        gestureTyping = prefs[GESTURE_TYPING] ?: gestureTyping,
        gestureTrail = prefs[GESTURE_TRAIL] ?: gestureTrail,
        gestureSensitivity = prefs[GESTURE_SENSITIVITY] ?: gestureSensitivity
    )

    private fun KeyboardSettings.withDictation(prefs: Preferences) = copy(
        dictationEnabled = prefs[DICTATION_ENABLED] ?: dictationEnabled,
        dictationLanguage = prefs[DICTATION_LANGUAGE] ?: dictationLanguage,
        dictationMicSide = prefs[DICTATION_MIC_SIDE] ?: dictationMicSide,
        dictationSilenceMs = prefs[DICTATION_SILENCE] ?: dictationSilenceMs,
        dictationModelId = prefs[DICTATION_MODEL] ?: dictationModelId,
        modelDownloadWifiOnly = prefs[MODEL_WIFI_ONLY] ?: modelDownloadWifiOnly
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: KeyboardSettings) {
        this[HEIGHT] = s.heightPercent
        this[BOTTOM_MARGIN] = s.bottomMarginDp
        this[SIDE_MARGIN] = s.sideMarginDp
        this[EDGE_BOOST] = s.edgeKeyBoostPercent
        this[NUMBER_ROW] = s.numberRow
        this[LONG_PRESS] = s.longPressDelayMs
        this[HAPTIC] = s.hapticIntensity
        this[SOUND] = s.soundVolume
        this[AUTO_CAP] = s.autoCapitalize
        this[DOUBLE_SPACE] = s.doubleSpacePeriod
        this[SMART_PUNCT] = s.smartPunctuation
        this[SHOW_SUGGESTIONS] = s.showSuggestions
        this[AUTO_CORRECT] = s.autoCorrect
        this[LAYOUT] = s.letterLayoutId
        this[PRIVATE_ENDS_ON_CLOSE] = s.privateModeEndsOnClose
        this[CLIPBOARD_ENABLED] = s.clipboardEnabled
        this[CLIPBOARD_RETENTION] = s.clipboardRetention
        this[CLIPBOARD_MAX_ITEMS] = s.clipboardMaxItems
        this[EMOJI_SKIN_TONE] = s.emojiSkinTone
        this[EMOJI_RECENTS] = s.emojiRecents
        this[GESTURE_TYPING] = s.gestureTyping
        this[GESTURE_TRAIL] = s.gestureTrail
        this[GESTURE_SENSITIVITY] = s.gestureSensitivity
        this[DICTATION_ENABLED] = s.dictationEnabled
        this[DICTATION_LANGUAGE] = s.dictationLanguage
        this[DICTATION_MIC_SIDE] = s.dictationMicSide
        this[DICTATION_SILENCE] = s.dictationSilenceMs
        this[DICTATION_MODEL] = s.dictationModelId
        this[MODEL_WIFI_ONLY] = s.modelDownloadWifiOnly
    }

    private companion object {
        val DICTATION_ENABLED = booleanPreferencesKey("dictation_enabled")
        val DICTATION_LANGUAGE = stringPreferencesKey("dictation_language")
        val DICTATION_MIC_SIDE = stringPreferencesKey("dictation_mic_side")
        val DICTATION_SILENCE = intPreferencesKey("dictation_silence_ms")
        val DICTATION_MODEL = stringPreferencesKey("dictation_model_id")
        val MODEL_WIFI_ONLY = booleanPreferencesKey("model_download_wifi_only")
        val HEIGHT = intPreferencesKey("height_percent")
        val GESTURE_TYPING = booleanPreferencesKey("gesture_typing")
        val GESTURE_TRAIL = booleanPreferencesKey("gesture_trail")
        val GESTURE_SENSITIVITY = intPreferencesKey("gesture_sensitivity")
        val BOTTOM_MARGIN = intPreferencesKey("bottom_margin_dp")
        val SIDE_MARGIN = intPreferencesKey("side_margin_dp")
        val EDGE_BOOST = intPreferencesKey("edge_key_boost_percent")
        val NUMBER_ROW = booleanPreferencesKey("number_row")
        val LONG_PRESS = intPreferencesKey("long_press_delay_ms")
        val HAPTIC = intPreferencesKey("haptic_intensity")
        val SOUND = intPreferencesKey("sound_volume")
        val AUTO_CAP = booleanPreferencesKey("auto_capitalize")
        val DOUBLE_SPACE = booleanPreferencesKey("double_space_period")
        val SMART_PUNCT = booleanPreferencesKey("smart_punctuation")
        val SHOW_SUGGESTIONS = booleanPreferencesKey("show_suggestions")
        val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
        val LAYOUT = stringPreferencesKey("letter_layout_id")
        val PRIVATE_ENDS_ON_CLOSE = booleanPreferencesKey("private_mode_ends_on_close")
        val CLIPBOARD_ENABLED = booleanPreferencesKey("clipboard_enabled")
        val CLIPBOARD_RETENTION = stringPreferencesKey("clipboard_retention")
        val CLIPBOARD_MAX_ITEMS = intPreferencesKey("clipboard_max_items")
        val EMOJI_SKIN_TONE = intPreferencesKey("emoji_skin_tone")
        val EMOJI_RECENTS = stringPreferencesKey("emoji_recents")
    }
}
