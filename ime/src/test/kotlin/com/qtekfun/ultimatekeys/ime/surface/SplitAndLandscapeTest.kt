// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.layouts.CharKey
import com.qtekfun.ultimatekeys.layouts.KeyAction
import com.qtekfun.ultimatekeys.layouts.LayoutParser
import com.qtekfun.ultimatekeys.style.MicPlacement
import com.qtekfun.ultimatekeys.style.Presets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SplitAndLandscapeTest {
    private val layout = LayoutParser.parse(
        """{"id":"t","rows":[
            ["q","w","e","r","t","y","u","i","o","p"],
            [{"type":"switch_symbols","w":2},{"type":"globe"},",",{"type":"space","w":4},".",{"type":"enter","w":2}]
        ]}"""
    )

    @Test
    fun `a split row leaves the free space in the middle`() {
        val g = KeyGeometry(layout, width = 1000f, rowHeight = 50f, splitGap = 200f)
        val row = g.keys.filter { it.top == 0f }
        val t = row.first { (it.key as? CharKey)?.label == "t" }
        val y = row.first { (it.key as? CharKey)?.label == "y" }
        assertEquals(200f, y.left - t.right, 0.01f)
        assertEquals(500f - 100f, t.right, 0.01f)
        assertEquals(1000f, row.last().right, 0.01f)
    }

    @Test
    fun `the space bar is cut in two so each thumb has one`() {
        val g = KeyGeometry(layout, width = 1000f, rowHeight = 50f, splitGap = 200f)
        val spaces = g.keys.filter {
            it.top == 50f && (it.key as? com.qtekfun.ultimatekeys.layouts.ActionKey)?.action ==
                KeyAction.SPACE
        }
        assertEquals(2, spaces.size)
        assertEquals(200f, spaces[1].left - spaces[0].right, 0.01f)
    }

    @Test
    fun `a touch in the free space still resolves to a key`() {
        val g = KeyGeometry(layout, width = 1000f, rowHeight = 50f, splitGap = 200f)
        assertTrue(g.keyAt(500f, 25f) != null)
    }

    @Test
    fun `no gap keeps one block`() {
        val plain = KeyGeometry(layout, width = 1000f, rowHeight = 50f)
        val zero = KeyGeometry(layout, width = 1000f, rowHeight = 50f, splitGap = 0f)
        assertEquals(plain.keys, zero.keys)
    }

    @Test
    fun `portrait uses the normal height and sideways its own, lowered when too tall`() {
        val style = Presets.default
        val s = KeyboardSettings(heightPercent = 110, landscapeHeightPercent = 100)
        fun percent(screen: SidewaysSpec.Screen, settings: KeyboardSettings = s) =
            SidewaysSpec.effectiveHeightPercent(4, style, settings, false, screen)
        assertEquals(110, percent(SidewaysSpec.Screen(landscape = false, heightDp = 100)))
        assertEquals(100, percent(SidewaysSpec.Screen(landscape = true, heightDp = 900)))
        val capped = percent(SidewaysSpec.Screen(landscape = true, heightDp = 300))
        assertTrue(capped < 100)
        val total = SurfaceSpec.totalHeightDp(4, capped, s.bottomMarginDp, style, false, false)
        assertTrue(total <= 300 * SidewaysSpec.MAX_LANDSCAPE_SHARE + 0.5f, "total $total")
        assertEquals(
            SurfaceSpec.MIN_PERCENT,
            percent(SidewaysSpec.Screen(landscape = true, heightDp = 40))
        )
    }

    @Test
    fun `the split follows the mode and spares the number and phone pads`() {
        fun split(mode: String, landscape: Boolean, page: Page = Page.LETTERS) =
            SidewaysSpec.isSplit(KeyboardSettings(splitMode = mode), landscape, page)
        assertFalse(split(KeyboardSettings.SPLIT_OFF, landscape = true))
        assertTrue(split(KeyboardSettings.SPLIT_LANDSCAPE, landscape = true))
        assertTrue(split(KeyboardSettings.SPLIT_PORTRAIT, landscape = false))
        assertFalse(split(KeyboardSettings.SPLIT_PORTRAIT, landscape = true))
        assertFalse(split(KeyboardSettings.SPLIT_LANDSCAPE, landscape = false))
        assertTrue(split(KeyboardSettings.SPLIT_ALWAYS, landscape = false, page = Page.SYMBOLS_1))
        assertFalse(split(KeyboardSettings.SPLIT_ALWAYS, landscape = true, page = Page.PHONE))
    }

    @Test
    fun `sideways the strip is compact, the mic joins it and there is no bottom margin`() {
        val base = Presets.default.let {
            it.copy(bottomRow = it.bottomRow.copy(micPlacement = MicPlacement.BOTTOM_MARGIN))
        }
        assertEquals(base, SidewaysSpec.styleFor(base, landscape = false))
        val side = SidewaysSpec.styleFor(base, landscape = true)
        assertTrue(side.suggestionBar.heightDp <= SidewaysSpec.LANDSCAPE_BAR_DP)
        assertEquals(MicPlacement.SUGGESTION_BAR, side.bottomRow.micPlacement)
        val inRow = base.copy(
            bottomRow = base.bottomRow.copy(micPlacement = MicPlacement.BOTTOM_ROW)
        )
        assertEquals(
            MicPlacement.SUGGESTION_BAR,
            SidewaysSpec.styleFor(inRow, landscape = true).bottomRow.micPlacement
        )
        val settings = KeyboardSettings(bottomMarginDp = 30)
        assertEquals(0, SidewaysSpec.sizing(settings, landscape = true).bottomMarginDp)
        assertEquals(30, SidewaysSpec.sizing(settings, landscape = false).bottomMarginDp)
    }

    @Test
    fun `sideways the keys take their share of the width, centered`() {
        assertEquals(0f, SidewaysSpec.extraSidePx(2000f, landscape = false, percent = 80), 0.01f)
        assertEquals(200f, SidewaysSpec.extraSidePx(2000f, landscape = true, percent = 80), 0.01f)
        assertEquals(0f, SidewaysSpec.extraSidePx(2000f, landscape = true, percent = 100), 0.01f)
    }

    @Test
    fun `with the new sideways layout the rows are far taller than before`() {
        val old = Presets.default.let {
            it.copy(bottomRow = it.bottomRow.copy(micPlacement = MicPlacement.BOTTOM_MARGIN))
        }
        val screen = SidewaysSpec.Screen(landscape = true, heightDp = 400)
        fun rowDp(style: com.qtekfun.ultimatekeys.style.Style, settings: KeyboardSettings) =
            SurfaceSpec.rowDp(
                SidewaysSpec.effectiveHeightPercent(
                    5,
                    style,
                    settings,
                    SurfaceSpec.micInMargin(true, style),
                    screen
                )
            )
        val before = rowDp(old, KeyboardSettings(numberRow = true, landscapeHeightPercent = 100))
        val side = SidewaysSpec.styleFor(old, landscape = true)
        val after = rowDp(
            side,
            SidewaysSpec.sizing(
                KeyboardSettings(numberRow = true, landscapeHeightPercent = 100),
                landscape = true
            )
        )
        assertTrue(after > before * 1.2f, "before $before after $after")
    }
}
