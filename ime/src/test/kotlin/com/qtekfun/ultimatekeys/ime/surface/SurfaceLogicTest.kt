// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.layouts.ActionKey
import com.qtekfun.ultimatekeys.layouts.CharKey
import com.qtekfun.ultimatekeys.layouts.KeyAction
import com.qtekfun.ultimatekeys.layouts.LayoutParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceLogicTest {
    private val layout = LayoutParser.parse(
        """{"id":"t","rows":[["a","b",{"type":"delete","w":2}],["c",{"type":"space","w":3}]]}"""
    )

    @Test
    fun `places keys in proportion to their weights`() {
        val g = KeyGeometry(layout, width = 400f, rowHeight = 50f)
        assertEquals(5, g.keys.size)
        assertEquals(100f, g.keys[0].width, 0.01f)
        assertEquals(200f, g.keys[2].width, 0.01f)
        assertEquals(100f, g.keys[3].width, 0.01f)
        assertEquals(300f, g.keys[4].width, 0.01f)
        assertEquals(100f, g.height)
        assertEquals(50f, g.keys[3].top)
    }

    @Test
    fun `gaps shrink the keys but not the touch targets`() {
        val g = KeyGeometry(layout, width = 400f, rowHeight = 50f, gapX = 10f, gapY = 10f)
        val a = g.keys[0]
        assertTrue(a.width < 100f)
        // A touch in the gap between a and b resolves to the nearer key.
        val gapX = a.right + 1f
        assertEquals("a", (g.keyAt(gapX, 25f)?.key as CharKey).label)
        assertEquals("b", (g.keyAt(g.keys[1].left - 1f, 25f)?.key as CharKey).label)
    }

    @Test
    fun `touches outside the surface go to the nearest key`() {
        val g = KeyGeometry(layout, width = 400f, rowHeight = 50f)
        assertEquals("a", (g.keyAt(-30f, -30f)?.key as CharKey).label)
        assertEquals(KeyAction.SPACE, (g.keyAt(999f, 999f)?.key as ActionKey).action)
        assertEquals("c", (g.keyAt(10f, 70f)?.key as CharKey).label)
    }

    @Test
    fun `an empty geometry has no key`() {
        val g = KeyGeometry(layout, width = 400f, rowHeight = 50f)
        assertNotNull(g.keyAt(1f, 1f))
        val empty = KeyGeometry(layout.copy(rows = emptyList()), width = 400f, rowHeight = 50f)
        assertNull(empty.keyAt(1f, 1f))
    }

    @Test
    fun `delete repeat accelerates and then deletes two at a time`() {
        assertEquals(120L, KeyRepeat.intervalMs(0))
        assertTrue(KeyRepeat.intervalMs(5) < KeyRepeat.intervalMs(1))
        assertEquals(35L, KeyRepeat.intervalMs(500))
        assertEquals(1, KeyRepeat.charsPerTick(0))
        assertEquals(2, KeyRepeat.charsPerTick(20))
    }

    @Test
    fun `drag steps keep the remainder`() {
        val d = DragSteps(10f)
        assertEquals(0, d.add(4f))
        assertEquals(1, d.add(7f))
        assertEquals(-3, d.add(-31f))
        assertEquals(0, d.add(0f))
        d.reset()
        assertEquals(0, d.add(9f))
    }

    @Test
    fun `latency percentiles`() {
        val t = LatencyTracker(capacity = 100)
        assertNull(t.percentileMs(95.0))
        (1..100).forEach { t.record(it * 1_000_000L) }
        assertEquals(95.0, t.percentileMs(95.0)!!, 0.001)
        assertEquals(50.0, t.percentileMs(50.0)!!, 0.001)
        t.record(500_000_000L)
        assertEquals(100, t.size())
    }

    @Test
    fun `a side margin keeps the keys off the edge and touches there still reach the edge keys`() {
        val g = KeyGeometry(layout, width = 400f, rowHeight = 50f, sideInset = 20f)
        assertEquals(20f, g.keys.minOf { it.left }, 0.01f)
        assertEquals(380f, g.keys.maxOf { it.right }, 0.01f)
        val first = g.keys.first()
        assertEquals(first, g.keyAt(2f, 25f))
        val lastOfRow = g.keys.filter { it.top < 50f }.maxBy { it.right }
        assertEquals(lastOfRow, g.keyAt(398f, 25f))
    }

    @Test
    fun `edge boost widens only the letters at the ends of a row`() {
        val plain = KeyGeometry(layout, width = 400f, rowHeight = 50f)
        val boosted = KeyGeometry(layout, width = 400f, rowHeight = 50f, edgeBoost = 0.5f)
        // Row one is a, b, delete: only "a" (a letter at the start) is widened; the rest of the row
        // gives up that space and the row still fills the whole width.
        assertTrue(boosted.keys[0].width > plain.keys[0].width)
        assertTrue(boosted.keys[1].width < plain.keys[1].width)
        assertTrue(boosted.keys[2].width < plain.keys[2].width)
        val row = boosted.keys.filter { it.top < 50f }
        assertEquals(400f, row.sumOf { it.width.toDouble() }.toFloat(), 0.01f)
    }
}
