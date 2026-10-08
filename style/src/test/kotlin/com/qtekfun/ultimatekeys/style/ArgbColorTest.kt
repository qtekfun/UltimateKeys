// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArgbColorTest {
    @Test
    fun `parses and prints both hex forms`() {
        assertEquals(ArgbColor(0xFF112233.toInt()), ArgbColor.parse("#112233"))
        assertEquals("#112233", ArgbColor.parse("112233")!!.toHex())
        assertEquals("#80112233", ArgbColor.parse("#80112233")!!.toHex())
        assertEquals(0x80, ArgbColor.parse("#80112233")!!.alpha)
    }

    @Test
    fun `rejects malformed colours`() {
        listOf("", "#12", "#GGGGGG", "#1234567", "red", "#-12345").forEach {
            assertNull(ArgbColor.parse(it), it)
        }
    }

    @Test
    fun `contrast matches the WCAG reference values`() {
        val black = ArgbColor.rgb(0x000000)
        val white = ArgbColor.rgb(0xFFFFFF)
        assertEquals(21.0, contrastRatio(black, white), 0.01)
        assertEquals(1.0, contrastRatio(white, white), 0.001)
        assertTrue(meetsAa(black, white))
        assertFalse(meetsAa(ArgbColor.rgb(0x999999), white))
    }

    @Test
    fun `default palettes have readable labels`() {
        listOf(Palette.DefaultLight, Palette.DefaultDark).forEach {
            assertTrue(meetsAa(it.labelText, it.keyLetter))
            assertTrue(meetsAa(it.actionLabelText, it.keyAction))
            assertTrue(meetsAa(it.barText, it.barBackground))
        }
    }
}
