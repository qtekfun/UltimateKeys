// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.qtekfun.ultimatekeys.style.Style
import com.qtekfun.ultimatekeys.style.contrastRatio
import com.qtekfun.ultimatekeys.style.meetsAa
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicPaletteTest {
    /** A blue-ish wallpaper: tone 0 is white, 1000 is black, others interpolate. */
    private val blue = ToneSource(
        accent = { tone -> shade(0x3060FF, tone) },
        neutral = { tone -> shade(0x808080, tone) },
        neutralVariant = { tone -> shade(0x7080A0, tone) }
    )

    private fun shade(base: Int, tone: Int): Int {
        val t = tone / 1000f
        fun ch(shift: Int): Int {
            val c = base shr shift and 0xFF
            return if (t <
                0.5f
            ) {
                (c + (255 - c) * (1 - t * 2)).toInt()
            } else {
                (c * (1 - t) * 2).toInt()
            }
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    @Test
    fun `dynamic palettes keep labels readable in both variants`() {
        listOf(false, true).forEach { dark ->
            val p = DynamicPalette.build(blue, dark)
            assertTrue(meetsAa(p.labelText, p.keyLetter), "label on key, dark=$dark")
            assertTrue(meetsAa(p.barText, p.barBackground), "bar text, dark=$dark")
            assertTrue(
                contrastRatio(p.actionLabelText, p.keyAction) >= 3.0,
                "action label, dark=$dark"
            )
        }
    }

    @Test
    fun `dynamic colours replace the palette only when asked for`() {
        val plain = SurfaceStyle.resolve(Style(), systemDark = false, tones = blue)
        val dynamic = SurfaceStyle.resolve(
            Style(dynamicColors = true),
            systemDark = false,
            tones = blue
        )
        assertNotEquals(plain.actionKey, dynamic.actionKey)
        assertEquals(Color(blue.accent(600)), dynamic.actionKey)
        assertTrue(dynamic.background is SolidColor)
    }

    @Test
    fun `without system colours a dynamic style falls back to its own palette`() {
        val style = Style(dynamicColors = true)
        assertEquals(
            SurfaceStyle.resolve(Style(), false),
            SurfaceStyle.resolve(style, false, tones = null)
        )
    }
}
