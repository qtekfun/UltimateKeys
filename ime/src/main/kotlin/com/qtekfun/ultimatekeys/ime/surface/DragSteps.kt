// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import kotlin.math.abs

/**
 * Turns a horizontal drag into whole steps (one step every [stepPx] pixels), keeping the
 * remainder. Used by the spacebar cursor control and the delete-key selection drag.
 */
class DragSteps(private val stepPx: Float) {
    private var accumulated = 0f

    /** Adds [dx] and returns the signed number of whole steps now reached (consumed). */
    fun add(dx: Float): Int {
        accumulated += dx
        val steps = (abs(accumulated) / stepPx).toInt()
        if (steps == 0) return 0
        val signed = if (accumulated < 0) -steps else steps
        accumulated -= signed * stepPx
        return signed
    }

    fun reset() {
        accumulated = 0f
    }
}
