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

class StripLayoutTest {
    @Test
    fun `without the button the three slots share the whole width`() {
        val layout = StripLayout(width = 300f, height = 40f, showToggle = false)
        assertEquals(0f, layout.toggleWidth)
        assertFalse(layout.isToggle(0f))
        assertEquals(listOf(0, 1, 2), listOf(10f, 150f, 290f).map(layout::slotAt))
    }

    @Test
    fun `the button takes a square at the left and the slots share the rest`() {
        val layout = StripLayout(width = 340f, height = 40f, showToggle = true)
        assertEquals(40f, layout.toggleWidth)
        assertTrue(layout.isToggle(39f))
        assertFalse(layout.isToggle(40f))
        assertEquals(100f, layout.cellWidth)
        assertEquals(listOf(0, 1, 2), listOf(41f, 141f, 339f).map(layout::slotAt))
    }
}

class MarginMicTest {
    @Test
    fun `the zone fills the margin under the keys at the right edge`() {
        val zone = MarginMic.of(
            width = 400f,
            keysBottom = 300f,
            totalHeight = 340f,
            onLeft = false
        )!!
        assertEquals(400f, zone.right)
        assertEquals(300f, zone.top)
        assertEquals(340f, zone.bottom)
        assertEquals(336f, zone.left, 0.01f)
        assertEquals(20f, zone.iconSize)
        assertTrue(zone.contains(390f, 320f))
        assertFalse(zone.contains(100f, 320f))
        assertFalse(zone.contains(390f, 250f))
    }

    @Test
    fun `the zone defaults to the left so it clears the system keyboard switcher`() {
        val zone = MarginMic.of(width = 400f, keysBottom = 300f, totalHeight = 340f)!!
        assertEquals(0f, zone.left)
        assertEquals(64f, zone.right, 0.01f)
        assertTrue(zone.contains(10f, 320f))
        assertFalse(zone.contains(390f, 320f))
    }

    @Test
    fun `there is no zone without a margin or a width`() {
        assertEquals(null, MarginMic.of(400f, 300f, 300f))
        assertEquals(null, MarginMic.of(0f, 300f, 340f))
    }

    @Test
    fun `the margin grows to fit the microphone but never shrinks a bigger one`() {
        assertEquals(40f, SurfaceSpec.marginDp(12, micInMargin = true))
        assertEquals(60f, SurfaceSpec.marginDp(60, micInMargin = true))
        assertEquals(12f, SurfaceSpec.marginDp(12, micInMargin = false))
        val plain = SurfaceSpec.totalHeightDp(4, 100, 12, Style())
        val withMic = SurfaceSpec.totalHeightDp(4, 100, 12, Style(), micInMargin = true)
        assertEquals(28f, withMic - plain, 0.001f)
    }

    @Test
    fun `by default the dictation key is in the margin, not in a key of the bottom row`() {
        assertEquals(
            com.qtekfun.ultimatekeys.style.MicPlacement.BOTTOM_MARGIN,
            Style().bottomRow.micPlacement
        )
    }
}
