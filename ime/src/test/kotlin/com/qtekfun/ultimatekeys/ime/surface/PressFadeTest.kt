// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.layouts.CharKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PressFadeTest {
    private val key = PlacedKey(CharKey(label = "a"), 0f, 0f, 10f, 10f)
    private val other = PlacedKey(CharKey(label = "b"), 10f, 0f, 20f, 10f)
    private val ms = 1_000_000L

    @Test
    fun `a pressed key is fully shown at once and does not animate`() {
        val fade = PressFade()
        fade.update(setOf(key), 10 * ms)
        assertEquals(1f, fade.level(key))
        assertEquals(0f, fade.level(other))
        assertFalse(fade.animating)
    }

    @Test
    fun `a released key fades out over time and then disappears`() {
        val fade = PressFade()
        fade.update(setOf(key), 10 * ms)
        fade.update(emptySet(), 11 * ms)
        assertTrue(fade.animating)
        assertTrue(fade.level(key) in 0f..1f && fade.level(key) < 1f)
        fade.update(emptySet(), 60 * ms)
        val mid = fade.level(key)
        assertTrue(mid > 0f && mid < 0.9f)
        fade.update(emptySet(), 400 * ms)
        assertEquals(0f, fade.level(key))
        assertFalse(fade.animating)
    }

    @Test
    fun `pressing again before the fade ends brings the key back to full`() {
        val fade = PressFade()
        fade.update(setOf(key), 10 * ms)
        fade.update(emptySet(), 40 * ms)
        fade.update(setOf(key), 50 * ms)
        assertEquals(1f, fade.level(key))
        assertFalse(fade.animating)
    }
}
