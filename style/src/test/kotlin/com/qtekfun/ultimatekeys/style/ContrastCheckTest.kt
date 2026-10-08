// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContrastCheckTest {
    @Test
    fun `presets raise no warnings`() {
        Presets.all.forEach {
            assertEquals(emptyList<ContrastWarning>(), ContrastCheck.warnings(it), it.id)
        }
    }

    @Test
    fun `unreadable labels are reported for the variant they are in`() {
        val grey = ArgbColor.rgb(0x888888)
        val style = Style(light = Palette.DefaultLight.copy(labelText = grey, keyLetter = grey))
        val warnings = ContrastCheck.warnings(style)
        assertTrue(warnings.any { !it.dark && it.pair == ContrastPair.LABEL_ON_LETTER_KEY })
        assertTrue(warnings.none { it.dark })
        assertEquals(
            1.0,
            warnings.first {
                it.pair == ContrastPair.LABEL_ON_LETTER_KEY
            }.ratio,
            0.01
        )
    }
}
