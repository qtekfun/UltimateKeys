// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import com.qtekfun.ultimatekeys.core.FakeSettingsRepository
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GlobeMenuTest {
    @Test
    fun `the language key menu opens the settings or the keyboard picker`() = runTest {
        val calls = mutableListOf<String>()
        val controller = KeyboardController(
            logic = InputLogic(),
            repository = FakeSettingsRepository(),
            scope = backgroundScope,
            feedback = null,
            openSettings = { calls += "settings" },
            showImePicker = { calls += "picker" }
        )
        controller.onGlobeMenu(GLOBE_MENU_SETTINGS)
        controller.onGlobeMenu(GLOBE_MENU_KEYBOARDS)
        controller.onGlobeMenu(-1)
        assertEquals(listOf("settings", "picker"), calls)
    }
}
