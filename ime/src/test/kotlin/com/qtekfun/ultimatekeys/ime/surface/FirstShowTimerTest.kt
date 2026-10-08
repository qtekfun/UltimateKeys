// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirstShowTimerTest {
    private var now = 0L
    private val lines = mutableListOf<String>()
    private val timer = FirstShowTimer({ now }, { lines += it })

    @Test
    fun `reports the time from creation to the first frame once`() {
        now = 1_000_000_000L
        timer.markCreated()
        now += 123_000_000L
        timer.markDrawn()
        now += 500_000_000L
        timer.markDrawn()
        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("first-show 123 ms"))
    }

    @Test
    fun `a frame before the service was created is ignored`() {
        timer.markDrawn()
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `creating again does not restart the clock`() {
        now = 10L
        timer.markCreated()
        now = 5_000_010L
        timer.markCreated()
        now = 8_000_010L
        timer.markDrawn()
        assertTrue(lines.single().startsWith("first-show 8 ms"))
    }
}
