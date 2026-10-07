// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

/** Auto-repeat timing for the delete key: starts slow and accelerates, then deletes faster. */
object KeyRepeat {
    const val INITIAL_DELAY_MS = 400L
    private const val START_INTERVAL_MS = 120L
    private const val MIN_INTERVAL_MS = 35L
    private const val STEP_MS = 8L
    private const val FAST_AFTER = 20

    /** Delay before repeat number [index] (0-based, after the initial delay). */
    fun intervalMs(index: Int): Long = maxOf(MIN_INTERVAL_MS, START_INTERVAL_MS - STEP_MS * index)

    /** Characters removed by repeat number [index]: two per tick once it is clearly held down. */
    fun charsPerTick(index: Int): Int = if (index >= FAST_AFTER) 2 else 1
}
