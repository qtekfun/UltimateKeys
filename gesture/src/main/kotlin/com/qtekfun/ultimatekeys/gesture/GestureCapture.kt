// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

import kotlin.math.hypot

/**
 * Records one finger's path and decides when a touch has become a gesture.
 *
 * A touch is a gesture once the finger is [startDistance] pixels away from where it landed. Until then it
 * is still an ordinary key press (the surface keeps its slide-to-neighbour correction); from then on the
 * touch belongs to the gesture and the key is not typed. Points closer than [minStep] to the previous one
 * are dropped, which keeps the path short without losing shape.
 *
 * Not thread-safe: the surface calls it from the main thread and hands [points] to the decoder.
 */
class GestureCapture(
    private val startDistance: Float,
    private val minStep: Float = DEFAULT_MIN_STEP
) {
    private var xy = FloatArray(INITIAL_CAPACITY * 2)
    private var count = 0
    private var startX = 0f
    private var startY = 0f

    /** True once the finger has travelled far enough from where it landed. */
    var isGesture = false
        private set

    /** Length of the recorded path in pixels. */
    var length = 0f
        private set

    val pointCount: Int get() = count

    fun begin(x: Float, y: Float) {
        count = 0
        length = 0f
        isGesture = false
        startX = x
        startY = y
        append(x, y)
    }

    /** Adds a point. Returns true when this point is the one that turned the touch into a gesture. */
    fun add(x: Float, y: Float): Boolean {
        if (count == 0) return false
        val lastX = xy[2 * count - 2]
        val lastY = xy[2 * count - 1]
        val step = hypot(x - lastX, y - lastY)
        if (step < minStep) return false
        length += step
        append(x, y)
        if (!isGesture && hypot(x - startX, y - startY) >= startDistance) {
            isGesture = true
            return true
        }
        return false
    }

    /** Makes sure the last position, however close to the previous point, ends the path. */
    fun finish(x: Float, y: Float) {
        if (count == 0) return
        val lastX = xy[2 * count - 2]
        val lastY = xy[2 * count - 1]
        val step = hypot(x - lastX, y - lastY)
        if (step > 0f) {
            length += step
            append(x, y)
        }
    }

    /** A copy of the path, interleaved `[x0, y0, x1, y1, ...]`. */
    fun points(): FloatArray = xy.copyOf(count * 2)

    /** The last [max] points, for drawing the trail. */
    fun tail(max: Int): FloatArray {
        val n = minOf(max, count)
        return xy.copyOfRange((count - n) * 2, count * 2)
    }

    private fun append(x: Float, y: Float) {
        if (count * 2 == xy.size) xy = xy.copyOf(xy.size * 2)
        xy[2 * count] = x
        xy[2 * count + 1] = y
        count++
    }

    companion object {
        const val DEFAULT_MIN_STEP = 1.5f
        private const val INITIAL_CAPACITY = 64
    }
}
