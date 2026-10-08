// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

import kotlin.math.hypot

/**
 * Geometry on paths stored as interleaved coordinates `[x0, y0, x1, y1, ...]`. Allocation-free where it
 * matters: the decoder calls [resample] once per candidate word.
 */
object PathMath {
    /** Number of points in an interleaved path of [size] floats. */
    fun pointCount(size: Int): Int = size / 2

    /** Total length of the first [count] points of [path]. */
    fun length(path: FloatArray, count: Int = pointCount(path.size)): Float {
        var total = 0f
        for (i in 1 until count) {
            total += hypot(path[2 * i] - path[2 * i - 2], path[2 * i + 1] - path[2 * i - 1])
        }
        return total
    }

    /** Length of the segment that ends at point [index]. */
    private fun segmentLength(path: FloatArray, index: Int): Float =
        hypot(path[2 * index] - path[2 * index - 2], path[2 * index + 1] - path[2 * index - 1])

    /**
     * Writes into [out] (size `2 * n`) [n] points spaced at equal arc length along the first [count] points
     * of [path], the first and last points included. A path without length repeats its first point.
     */
    fun resample(path: FloatArray, count: Int, n: Int, out: FloatArray) {
        require(n >= 2) { "A resampled path needs at least two points" }
        require(count >= 1) { "Cannot resample an empty path" }
        val total = length(path, count)
        if (total <= 0f) {
            for (i in 0 until n) {
                out[2 * i] = path[0]
                out[2 * i + 1] = path[1]
            }
            return
        }
        val step = total / (n - 1)
        out[0] = path[0]
        out[1] = path[1]
        var written = 1
        var segment = 1
        var segmentStart = 0f
        var segmentLength = segmentLength(path, 1)
        while (written < n - 1) {
            val target = step * written
            while (segmentStart + segmentLength < target && segment < count - 1) {
                segmentStart += segmentLength
                segment++
                segmentLength = segmentLength(path, segment)
            }
            val t = if (segmentLength >
                0f
            ) {
                ((target - segmentStart) / segmentLength).coerceIn(0f, 1f)
            } else {
                0f
            }
            out[2 * written] =
                path[2 * segment - 2] + t * (path[2 * segment] - path[2 * segment - 2])
            out[2 * written + 1] =
                path[2 * segment - 1] + t * (path[2 * segment + 1] - path[2 * segment - 1])
            written++
        }
        out[2 * n - 2] = path[2 * count - 2]
        out[2 * n - 1] = path[2 * count - 1]
    }
}
