// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyboardGeometryTest {
    private val geometry = KeyboardGeometry.qwertyFor(Locale.ENGLISH)

    private fun cellAt(chars: IntArray, x: Int, y: Int): List<Int> {
        val cellWidth =
            (geometry.width + KeyboardGeometry.GRID_WIDTH - 1) / KeyboardGeometry.GRID_WIDTH
        val cellHeight =
            (geometry.height + KeyboardGeometry.GRID_HEIGHT - 1) / KeyboardGeometry.GRID_HEIGHT
        val start =
            ((y / cellHeight) * KeyboardGeometry.GRID_WIDTH + x / cellWidth) *
                KeyboardGeometry.MAX_PROXIMITY_CHARS
        return chars.slice(start until start + KeyboardGeometry.MAX_PROXIMITY_CHARS).filter {
            it >=
                0
        }
    }

    private fun centerOf(letter: Char): Pair<Int, Int> {
        val key = geometry.keys.first { it.codePoint == letter.code }
        return key.x + key.width / 2 to key.y + key.height / 2
    }

    @Test
    fun `proximity array has the shape the native code expects`() {
        val chars = geometry.proximityChars()

        assertEquals(
            KeyboardGeometry.GRID_WIDTH * KeyboardGeometry.GRID_HEIGHT *
                KeyboardGeometry.MAX_PROXIMITY_CHARS,
            chars.size
        )
    }

    @Test
    fun `the key under a point comes first followed by its neighbours`() {
        val chars = geometry.proximityChars()
        val (x, y) = centerOf('g')

        val near = cellAt(chars, x, y)

        assertEquals('g'.code, near.first())
        assertTrue(near.containsAll(listOf('f'.code, 'h'.code, 't'.code, 'v'.code)))
        assertTrue(near.size <= KeyboardGeometry.MAX_PROXIMITY_CHARS)
    }

    @Test
    fun `far keys are not neighbours`() {
        val (x, y) = centerOf('q')

        val near = cellAt(geometry.proximityChars(), x, y)

        assertTrue('p'.code !in near)
        assertTrue('m'.code !in near)
    }

    @Test
    fun `spanish gets the enye key and english does not`() {
        val spanish = KeyboardGeometry.qwertyFor(Locale.forLanguageTag("es"))

        assertTrue(spanish.keys.any { it.codePoint == 'ñ'.code })
        assertTrue(geometry.keys.none { it.codePoint == 'ñ'.code })
        assertEquals(100, spanish.mostCommonKeyWidth)
    }
}
