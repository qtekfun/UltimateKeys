// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import androidx.compose.ui.graphics.Color
import com.qtekfun.ultimatekeys.layouts.LayoutParser
import com.qtekfun.ultimatekeys.style.Appearance
import com.qtekfun.ultimatekeys.style.KeyShape
import com.qtekfun.ultimatekeys.style.Palette
import com.qtekfun.ultimatekeys.style.Style
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceStyleTest {
    private val layout = LayoutParser.parse("""{"id":"t","rows":[["1"],["a"],["b"],["c"]]}""")

    @Test
    fun `the variant follows the system unless the style forces one`() {
        assertTrue(SurfaceStyle.isDark(Style(), systemDark = true))
        assertFalse(SurfaceStyle.isDark(Style(), systemDark = false))
        assertFalse(SurfaceStyle.isDark(Style(appearance = Appearance.LIGHT), systemDark = true))
        assertTrue(SurfaceStyle.isDark(Style(appearance = Appearance.DARK), systemDark = false))
    }

    @Test
    fun `colours come from the chosen palette`() {
        val dark = SurfaceStyle.resolve(Style(), systemDark = true)
        val light = SurfaceStyle.resolve(Style(), systemDark = false)
        assertEquals(Color(Palette.DefaultDark.keyLetter.argb), dark.letterKey)
        assertEquals(Color(Palette.DefaultLight.keyLetter.argb), light.letterKey)
    }

    @Test
    fun `border width is zero when the border is off`() {
        assertEquals(0f, SurfaceStyle.resolve(Style(), false).borderWidthDp)
        val bordered = Style(keys = KeyShape(border = true, borderWidthDp = 2f))
        assertEquals(2f, SurfaceStyle.resolve(bordered, false).borderWidthDp)
        assertTrue(SurfaceStyle.resolve(bordered, false).hasBorder)
    }

    @Test
    fun `row scales apply to number letter and bottom rows`() {
        val style = Style(
            keys = KeyShape(
                numberRowHeightPercent = 60,
                letterRowHeightPercent = 100,
                bottomRowHeightPercent = 120
            )
        )
        assertEquals(listOf(0.6f, 1f, 1f, 1.2f), SurfaceSpec.rowScales(style, 4, numberRow = true))
        assertEquals(listOf(1f, 1f, 1f, 1.2f), SurfaceSpec.rowScales(style, 4, numberRow = false))
    }

    @Test
    fun `geometry stacks rows of different heights`() {
        val g =
            KeyGeometry(layout, width = 100f, rowHeight = 50f, rowScales = listOf(0.5f, 1f, 1f, 2f))
        assertEquals(225f, g.height, 0.01f)
        assertEquals(listOf(0f, 25f, 75f, 125f), g.keys.map { it.top })
        assertEquals(100f, g.keys.last().height, 0.01f)
    }

    @Test
    fun `total height grows with the number row`() {
        val style = Style()
        val without = SurfaceSpec.totalHeightDp(4, 100, 0, style, numberRow = false)
        val with = SurfaceSpec.totalHeightDp(4, 100, 0, style, numberRow = true)
        assertTrue(with < without, "number row is shorter than a letter row by default")
    }
}
