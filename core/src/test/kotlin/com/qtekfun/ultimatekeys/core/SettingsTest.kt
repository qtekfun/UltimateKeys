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
            longPressDelayMs = 1,
            hapticIntensity = -4,
            soundVolume = 900
        ).sanitized()
        assertEquals(130, s.heightPercent)
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
        repo.update { it.copy(numberRow = true, heightPercent = 999, letterLayoutId = "en_qwerty") }
        val s = repo.settings.first()
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
}
