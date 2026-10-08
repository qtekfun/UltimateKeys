// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PathMathTest {
    @Test
    fun `length of a right angle`() {
        assertEquals(7f, PathMath.length(floatArrayOf(0f, 0f, 3f, 0f, 3f, 4f)), 1e-5f)
    }

    @Test
    fun `resampling spaces points evenly and keeps both ends`() {
        val out = FloatArray(2 * 5)
        PathMath.resample(floatArrayOf(0f, 0f, 10f, 0f, 10f, 30f), 3, 5, out)
        // Total length 40, step 10.
        assertArrayEquals(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 10f, 20f, 10f, 30f), out, 1e-4f)
    }

    @Test
    fun `resampling a path without length repeats its point`() {
        val out = FloatArray(2 * 4)
        PathMath.resample(floatArrayOf(5f, 6f, 5f, 6f), 2, 4, out)
        assertArrayEquals(floatArrayOf(5f, 6f, 5f, 6f, 5f, 6f, 5f, 6f), out)
    }

    @Test
    fun `a single point resamples to itself`() {
        val out = FloatArray(2 * 3)
        PathMath.resample(floatArrayOf(1f, 2f), 1, 3, out)
        assertArrayEquals(floatArrayOf(1f, 2f, 1f, 2f, 1f, 2f), out)
    }

    @Test
    fun `resampling rejects degenerate requests`() {
        assertThrows(IllegalArgumentException::class.java) {
            PathMath.resample(floatArrayOf(0f, 0f), 1, 1, FloatArray(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PathMath.resample(FloatArray(0), 0, 4, FloatArray(8))
        }
    }

    @Test
    fun `resampling copes with zero length segments in the middle`() {
        val out = FloatArray(2 * 3)
        PathMath.resample(floatArrayOf(0f, 0f, 0f, 0f, 10f, 0f), 3, 3, out)
        assertArrayEquals(floatArrayOf(0f, 0f, 5f, 0f, 10f, 0f), out, 1e-4f)
    }
}

class GestureAlphabetTest {
    @Test
    fun `plain words collapse repeated letters`() {
        assertArrayEquals(
            byteArrayOf(11, 4, 21, 4, 11),
            GestureAlphabet.sequenceOf("level")
        )
        assertArrayEquals(byteArrayOf(7, 4, 11, 14), GestureAlphabet.sequenceOf("hello"))
    }

    @Test
    fun `accents are dropped but enye and c cedilla keep their own key`() {
        assertEquals(
            GestureAlphabet.sequenceOf("esta")!!.toList(),
            GestureAlphabet.sequenceOf("está")!!.toList()
        )
        assertEquals(
            GestureAlphabet.LETTERS.indexOf('ñ').toByte(),
            GestureAlphabet.sequenceOf("año")!![1]
        )
        assertEquals(
            GestureAlphabet.LETTERS.indexOf('ç').toByte(),
            GestureAlphabet.sequenceOf("açai")!![1]
        )
        assertNotNull(GestureAlphabet.sequenceOf("pingüino"))
    }

    @Test
    fun `apostrophes are skipped and case does not matter`() {
        assertEquals(
            GestureAlphabet.sequenceOf("dont")!!.toList(),
            GestureAlphabet.sequenceOf("Don't")!!.toList()
        )
        assertEquals(
            GestureAlphabet.sequenceOf("dont")!!.toList(),
            GestureAlphabet.sequenceOf("don’t")!!.toList()
        )
    }

    @Test
    fun `words that cannot be traced are rejected`() {
        assertNull(GestureAlphabet.sequenceOf("a"))
        assertNull(GestureAlphabet.sequenceOf("aaa"))
        assertNull(GestureAlphabet.sequenceOf("e-mail"))
        assertNull(GestureAlphabet.sequenceOf("b2b"))
        assertNull(GestureAlphabet.sequenceOf("straße"))
        assertNull(GestureAlphabet.sequenceOf("日本"))
        assertNull(GestureAlphabet.sequenceOf(""))
    }

    @Test
    fun `key indices follow the layout letters`() {
        assertEquals(0, GestureAlphabet.keyIndex('a'))
        assertEquals(0, GestureAlphabet.keyIndex('A'))
        assertEquals(-1, GestureAlphabet.keyIndex('7'))
    }
}

class GestureKeyboardTest {
    private val keyboard = TestKeyboard.keyboard()

    @Test
    fun `unit is the key size`() {
        assertEquals(110f, keyboard.unit, 1f)
    }

    @Test
    fun `path length sums the distances between key centres`() {
        val q = GestureAlphabet.keyIndex('q').toByte()
        val w = GestureAlphabet.keyIndex('w').toByte()
        val e = GestureAlphabet.keyIndex('e').toByte()
        assertEquals(200f, keyboard.pathLength(byteArrayOf(q, w, e)), 1e-3f)
        assertEquals(100f, keyboard.pathLength(byteArrayOf(q, w, e), 1, 3), 1e-3f)
    }

    @Test
    fun `a layout without a letter reports it as missing`() {
        val n = GestureAlphabet.keyIndex('ñ')
        assertFalse(keyboard.has(n))
        assertTrue(keyboard.pathLength(byteArrayOf(0, n.toByte())).isNaN())
    }

    @Test
    fun `keys near a point are sorted by distance and never empty`() {
        val (x, y) = TestKeyboard.center('g')
        val near = keyboard.keysNear(x + 10f, y, 120f).map { GestureAlphabet.LETTERS[it] }
        assertEquals('g', near.first())
        assertTrue('h' in near && 'f' in near)
        assertFalse('p' in near)
        val far = keyboard.keysNear(-5000f, -5000f, 10f)
        assertEquals(1, far.size)
    }

    @Test
    fun `duplicate and unknown keys are ignored`() {
        val keys = TestKeyboard.keys() + GestureKey('q', 900f, 900f, 1000f, 1000f) +
            GestureKey('7', 0f, 0f, 1f, 1f)
        val board = GestureKeyboard(keys)
        assertEquals(TestKeyboard.center('q').first, board.centerX[GestureAlphabet.keyIndex('q')])
    }

    @Test
    fun `an empty keyboard is empty`() {
        val board = GestureKeyboard(emptyList())
        assertTrue(board.isEmpty)
        assertEquals(1f, board.unit)
    }

    @Test
    fun `ends near checks the first and last key`() {
        val path = TestKeyboard.pathOf("we")
        assertTrue(keyboard.endsNear(GestureAlphabet.sequenceOf("we")!!, path, 2, 60f))
        assertFalse(keyboard.endsNear(GestureAlphabet.sequenceOf("wr")!!, path, 2, 60f))
        assertFalse(keyboard.endsNear(GestureAlphabet.sequenceOf("ñe")!!, path, 2, 60f))
    }
}

class GestureCaptureTest {
    @Test
    fun `a touch becomes a gesture once it has travelled far enough`() {
        val capture = GestureCapture(startDistance = 50f)
        capture.begin(0f, 0f)
        assertFalse(capture.add(20f, 0f))
        assertFalse(capture.isGesture)
        assertTrue(capture.add(60f, 0f))
        assertTrue(capture.isGesture)
        assertFalse(capture.add(90f, 0f))
        assertEquals(90f, capture.length, 1e-3f)
    }

    @Test
    fun `points that barely move are dropped`() {
        val capture = GestureCapture(startDistance = 50f, minStep = 5f)
        capture.begin(0f, 0f)
        capture.add(1f, 1f)
        capture.add(2f, 0f)
        assertEquals(1, capture.pointCount)
        capture.add(10f, 0f)
        assertEquals(2, capture.pointCount)
    }

    @Test
    fun `the last position always ends the path`() {
        val capture = GestureCapture(startDistance = 50f, minStep = 5f)
        capture.begin(0f, 0f)
        capture.add(20f, 0f)
        capture.finish(22f, 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 20f, 0f, 22f, 0f), capture.points())
        capture.finish(22f, 0f)
        assertEquals(3, capture.pointCount)
    }

    @Test
    fun `the tail keeps the newest points and the storage grows`() {
        val capture = GestureCapture(startDistance = 5f, minStep = 1f)
        capture.begin(0f, 0f)
        repeat(300) { capture.add(it + 2f, 0f) }
        assertEquals(301, capture.pointCount)
        assertArrayEquals(floatArrayOf(300f, 0f, 301f, 0f), capture.tail(2))
        assertEquals(301 * 2, capture.tail(10_000).size)
    }

    @Test
    fun `begin starts over`() {
        val capture = GestureCapture(startDistance = 5f)
        capture.begin(0f, 0f)
        capture.add(50f, 0f)
        capture.begin(7f, 7f)
        assertFalse(capture.isGesture)
        assertEquals(0f, capture.length)
        assertArrayEquals(floatArrayOf(7f, 7f), capture.points())
    }

    @Test
    fun `nothing happens before begin`() {
        val capture = GestureCapture(startDistance = 5f)
        assertFalse(capture.add(1f, 1f))
        capture.finish(1f, 1f)
        assertEquals(0, capture.pointCount)
    }

    @Test
    fun `sensitivity maps to a distance in key widths`() {
        assertTrue(
            GestureSensitivity.startDistanceUnits(0) > GestureSensitivity.startDistanceUnits(50)
        )
        assertTrue(
            GestureSensitivity.startDistanceUnits(50) > GestureSensitivity.startDistanceUnits(100)
        )
        assertEquals(
            GestureSensitivity.startDistanceUnits(100),
            GestureSensitivity.startDistanceUnits(500)
        )
        assertTrue(GestureSensitivity.startDistanceUnits(100) > 0.3f)
    }
}

class GestureConfigTest {
    @Test
    fun `invalid settings are refused`() {
        assertThrows(IllegalArgumentException::class.java) { GestureConfig(resamplePoints = 2) }
        assertThrows(IllegalArgumentException::class.java) {
            GestureConfig(shortlist = 2, maxCandidates = 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GestureConfig(maxCandidates = 0, shortlist = 0)
        }
        assertThrows(IllegalArgumentException::class.java) { GestureConfig(shapeSigma = 0f) }
    }
}
