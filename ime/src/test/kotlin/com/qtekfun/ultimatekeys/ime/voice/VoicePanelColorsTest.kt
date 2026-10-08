// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.voice

import androidx.compose.ui.graphics.Color
import com.qtekfun.ultimatekeys.style.Appearance
import com.qtekfun.ultimatekeys.style.Presets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class VoicePanelColorsTest {
    @Test
    fun `the panel uses the palette of the active variant`() {
        val style = Presets.default.copy(appearance = Appearance.FOLLOW_SYSTEM)
        val light = VoicePanelColors.resolve(style, systemDark = false)
        val dark = VoicePanelColors.resolve(style, systemDark = true)
        assertEquals(Color(style.light.panelBackground.argb), light.background)
        assertEquals(Color(style.dark.panelBackground.argb), dark.background)
        assertEquals(Color(style.light.keyAction.argb), light.accent)
        assertEquals(Color(style.dark.privateTint.argb), dark.privateTint)
        assertNotEquals(light.background, dark.background)
    }

    @Test
    fun `a fixed appearance ignores the system`() {
        val style = Presets.default.copy(appearance = Appearance.DARK)
        assertEquals(
            VoicePanelColors.resolve(style, systemDark = false),
            VoicePanelColors.resolve(style, systemDark = true)
        )
    }
}
