// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScreenProfileTest {
    @Test
    fun `a device that does not fold is always normal`() {
        assertEquals(ScreenProfile.NORMAL, ScreenProfiles.decide(false, 360))
        assertEquals(ScreenProfile.NORMAL, ScreenProfiles.decide(false, 800))
    }

    @Test
    fun `a foldable gets the inner profile from 600 dp and the outer one below`() {
        assertEquals(ScreenProfile.FOLDABLE_OUTER, ScreenProfiles.decide(true, 360))
        assertEquals(ScreenProfile.FOLDABLE_OUTER, ScreenProfiles.decide(true, 599))
        assertEquals(ScreenProfile.FOLDABLE_INNER, ScreenProfiles.decide(true, 600))
        assertEquals(ScreenProfile.FOLDABLE_INNER, ScreenProfiles.decide(true, 700))
    }

    private fun TestScope.repo(profile: ScreenProfile) = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            Files.createTempFile("profiles", ".preferences_pb").toFile().apply { delete() }
        },
        flowOf(profile)
    )

    @Test
    fun `a foldable screen falls back to the original values`() = runTest {
        val r = repo(ScreenProfile.FOLDABLE_OUTER)
        r.updateOf(ScreenProfile.NORMAL) {
            it.copy(heightPercent = 90, landscapeHeightPercent = 60, splitMode = "always")
        }
        val outer = r.settingsOf(ScreenProfile.FOLDABLE_OUTER).first()
        assertEquals(90, outer.heightPercent)
        assertEquals(60, outer.landscapeHeightPercent)
        assertEquals("always", outer.splitMode)
        assertEquals(outer, r.settings.first())
    }

    @Test
    fun `each screen keeps its own size settings and shares the rest`() = runTest {
        val r = repo(ScreenProfile.FOLDABLE_INNER)
        r.updateOf(ScreenProfile.NORMAL) { it.copy(heightPercent = 90, sideMarginDp = 10) }
        r.update {
            it.copy(
                heightPercent = 120,
                bottomMarginDp = 30,
                landscapeWidthPercent = 70,
                landscapeMic = KeyboardSettings.LANDSCAPE_MIC_STRIP,
                splitGapPercent = 40,
                numberRow = true
            )
        }
        r.updateOf(ScreenProfile.FOLDABLE_OUTER) { it.copy(heightPercent = 75, sideMarginDp = 0) }

        val inner = r.settingsOf(ScreenProfile.FOLDABLE_INNER).first()
        val outer = r.settingsOf(ScreenProfile.FOLDABLE_OUTER).first()
        val normal = r.settingsOf(ScreenProfile.NORMAL).first()
        assertEquals(120, inner.heightPercent)
        assertEquals(30, inner.bottomMarginDp)
        assertEquals(70, inner.landscapeWidthPercent)
        assertEquals(KeyboardSettings.LANDSCAPE_MIC_STRIP, inner.landscapeMic)
        assertEquals(40, inner.splitGapPercent)
        assertEquals(10, inner.sideMarginDp)
        assertEquals(75, outer.heightPercent)
        assertEquals(0, outer.sideMarginDp)
        assertEquals(KeyboardSettings.LANDSCAPE_MIC_HIDDEN, outer.landscapeMic)
        // The original profile is untouched by the foldable screens.
        assertEquals(90, normal.heightPercent)
        assertEquals(10, normal.sideMarginDp)
        assertEquals(KeyboardSettings.DEFAULT_BOTTOM_MARGIN_DP, normal.bottomMarginDp)
        // Settings that are not about size are shared.
        assertEquals(true, inner.numberRow)
        assertEquals(true, outer.numberRow)
        assertEquals(true, normal.numberRow)
    }

    @Test
    fun `a foldable screen clamps its values and only follows the active profile`() = runTest {
        val r = repo(ScreenProfile.FOLDABLE_OUTER)
        r.updateOf(ScreenProfile.FOLDABLE_INNER) {
            it.copy(heightPercent = 999, landscapeHeightPercent = 1, splitMode = "diagonal")
        }
        val inner = r.settingsOf(ScreenProfile.FOLDABLE_INNER).first()
        assertEquals(KeyboardSettings.HEIGHT_RANGE.last, inner.heightPercent)
        assertEquals(KeyboardSettings.LANDSCAPE_HEIGHT_RANGE.first, inner.landscapeHeightPercent)
        assertEquals(KeyboardSettings.SPLIT_OFF, inner.splitMode)
        assertEquals(KeyboardSettings(), r.settings.first())
        assertEquals(ScreenProfile.FOLDABLE_OUTER, r.screenProfile.first())
    }

    @Test
    fun `the default repository and the fake behave as one normal screen`() = runTest {
        val fake = FakeSettingsRepository()
        fake.updateOf(ScreenProfile.FOLDABLE_INNER) { it.copy(heightPercent = 80) }
        assertEquals(80, fake.settingsOf(ScreenProfile.NORMAL).first().heightPercent)
        assertEquals(ScreenProfile.NORMAL, fake.screenProfile.first())
    }
}
