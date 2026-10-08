// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsTest {
    @Test
    fun `sanitized clamps every range`() {
        val s = KeyboardSettings(
            heightPercent = 500,
            bottomMarginDp = 999,
            longPressDelayMs = 1,
            hapticIntensity = -4,
            soundVolume = 900
        ).sanitized()
        assertEquals(130, s.heightPercent)
        assertEquals(48, s.bottomMarginDp)
        assertEquals(
            KeyboardSettings.MIC_LEFT,
            KeyboardSettings(dictationMicSide = "middle").sanitized().dictationMicSide
        )
        assertEquals(
            KeyboardSettings.MIC_CENTER,
            KeyboardSettings(dictationMicSide = "center").sanitized().dictationMicSide
        )
        assertEquals(
            KeyboardSettings.MIC_RIGHT,
            KeyboardSettings(dictationMicSide = "right").sanitized().dictationMicSide
        )
        assertEquals(150, s.longPressDelayMs)
        assertEquals(0, s.hapticIntensity)
        assertEquals(100, s.soundVolume)
    }

    @Test
    fun `datastore round trips and clamps`() = runTest {
        val file = Files.createTempFile("settings", ".preferences_pb").toFile().apply { delete() }
        val repo =
            DataStoreSettingsRepository(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    file
                }
            )
        assertEquals(KeyboardSettings(), repo.settings.first())
        repo.update {
            it.copy(
                numberRow = true,
                heightPercent = 999,
                letterLayoutId = "en_qwerty",
                gestureTyping = false,
                gestureTrail = false,
                gestureSensitivity = 900
            )
        }
        val s = repo.settings.first()
        assertEquals(false, s.gestureTyping)
        assertEquals(false, s.gestureTrail)
        assertEquals(100, s.gestureSensitivity)
        assertEquals(true, s.numberRow)
        assertEquals(130, s.heightPercent)
        assertEquals("en_qwerty", s.letterLayoutId)
    }

    @Test
    fun `fake repository behaves like the real one`() = runTest {
        val repo = FakeSettingsRepository()
        repo.update { it.copy(soundVolume = 300) }
        assertEquals(100, repo.settings.first().soundVolume)
    }

    @Test
    fun `clipboard and emoji settings round trip and clamp`() = runTest {
        val file = Files.createTempFile("settings", ".preferences_pb").toFile().apply { delete() }
        val repo = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        )
        repo.update {
            it.copy(
                clipboardEnabled = false,
                clipboardRetention = "week",
                clipboardMaxItems = 1,
                emojiSkinTone = 9,
                emojiRecents = "a b"
            )
        }
        val s = repo.settings.first()
        assertEquals(false, s.clipboardEnabled)
        assertEquals("week", s.clipboardRetention)
        assertEquals(5, s.clipboardMaxItems)
        assertEquals(5, s.emojiSkinTone)
        assertEquals("a b", s.emojiRecents)
    }

    @Test
    fun `dictation settings round trip, clamp and reject unknown languages`() = runTest {
        val file = Files.createTempFile("settings", ".preferences_pb").toFile().apply { delete() }
        val repo = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        )
        val defaults = repo.settings.first()
        assertEquals("auto", defaults.dictationLanguage)
        assertEquals(1500, defaults.dictationSilenceMs)
        assertEquals("", defaults.dictationModelId)
        assertEquals(true, defaults.modelDownloadWifiOnly)
        repo.update {
            it.copy(
                dictationLanguage = "es",
                dictationSilenceMs = 99_999,
                dictationModelId = "small",
                modelDownloadWifiOnly = false
            )
        }
        val s = repo.settings.first()
        assertEquals("es", s.dictationLanguage)
        assertEquals(5000, s.dictationSilenceMs)
        assertEquals("small", s.dictationModelId)
        assertEquals(false, s.modelDownloadWifiOnly)
        repo.update { it.copy(dictationLanguage = "fr", dictationSilenceMs = 1) }
        assertEquals("auto", repo.settings.first().dictationLanguage)
        assertEquals(500, repo.settings.first().dictationSilenceMs)
    }
}
