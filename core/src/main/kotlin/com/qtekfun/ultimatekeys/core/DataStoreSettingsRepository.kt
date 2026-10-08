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

    private fun Preferences.toSettings(): KeyboardSettings {
        val d = KeyboardSettings()
        return KeyboardSettings(
            heightPercent = this[HEIGHT] ?: d.heightPercent,
            bottomMarginDp = this[BOTTOM_MARGIN] ?: d.bottomMarginDp,
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
            privateModeEndsOnClose = this[PRIVATE_ENDS_ON_CLOSE] ?: d.privateModeEndsOnClose
        ).sanitized()
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: KeyboardSettings) {
        this[HEIGHT] = s.heightPercent
        this[BOTTOM_MARGIN] = s.bottomMarginDp
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
    }

    private companion object {
        val HEIGHT = intPreferencesKey("height_percent")
        val BOTTOM_MARGIN = intPreferencesKey("bottom_margin_dp")
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
    }
}
