// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.style.Presets
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageHeightTest {
    private val style = Presets.default

    private fun total(rows: Int, percent: Int, numberRow: Boolean, uniform: Boolean) =
        SurfaceSpec.totalHeightDp(rows, percent, 0, style, numberRow, false, uniform)

    @Test
    fun `the letters page keeps its own height`() {
        val letters = total(5, 100, numberRow = true, uniform = false)
        assertEquals(letters, total(5, 100, numberRow = true, uniform = false), 0f)
    }

    @Test
    fun `a page with fewer rows is stretched to the height of the letters page`() {
        val letters = total(5, 100, numberRow = true, uniform = false)
        val percent = SurfaceSpec.matchedHeightPercent(style, 5, true, 100, 4, false)
        assertTrue(percent > 100)
        assertTrue(abs(letters - total(4, percent, numberRow = false, uniform = false)) < 1.5f)
    }

    @Test
    fun `the number pad matches the letters page too`() {
        val letters = total(4, 100, numberRow = false, uniform = false)
        val percent = SurfaceSpec.matchedHeightPercent(style, 4, false, 100, 4, true)
        assertTrue(abs(letters - total(4, percent, numberRow = false, uniform = true)) < 1.5f)
    }
}
