// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

import kotlin.math.hypot
import kotlin.math.sqrt

/** A letter key as a pixel rectangle. */
data class GestureKey(
    val letter: Char,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * The letter keys of the layout on screen, which is what word templates are built from. Only keys whose
 * letter is part of [GestureAlphabet] are kept; the first key wins when a letter appears twice.
 *
 * [unit] is the size of one key (the geometric mean of the average width and height). Distances in the
 * decoder are measured in units so that tuning does not depend on the screen density.
 */
class GestureKeyboard(keys: List<GestureKey>) {
    private val byIndex = arrayOfNulls<GestureKey>(GestureAlphabet.size)

    /** Centre of each key by alphabet index; NaN for letters this layout lacks. */
    val centerX = FloatArray(GestureAlphabet.size) { Float.NaN }
    val centerY = FloatArray(GestureAlphabet.size) { Float.NaN }

    val unit: Float

    /** Distance between the centres of two keys, row-major by alphabet index; NaN when a key is missing. */
    private val distances = FloatArray(GestureAlphabet.size * GestureAlphabet.size)

    init {
        for (key in keys) {
            val index = GestureAlphabet.keyIndex(key.letter)
            if (index < 0 || byIndex[index] != null) continue
            byIndex[index] = key
            centerX[index] = key.centerX
            centerY[index] = key.centerY
        }
        val present = byIndex.filterNotNull()
        unit = if (present.isEmpty()) {
            1f
        } else {
            sqrt(present.map { it.width }.average() * present.map { it.height }.average()).toFloat()
        }
        for (a in 0 until GestureAlphabet.size) {
            for (b in 0 until GestureAlphabet.size) {
                distances[a * GestureAlphabet.size + b] =
                    hypot(centerX[a] - centerX[b], centerY[a] - centerY[b])
            }
        }
    }

    val isEmpty: Boolean get() = byIndex.all { it == null }

    fun has(index: Int): Boolean = byIndex[index] != null

    fun distance(a: Int, b: Int): Float = distances[a * GestureAlphabet.size + b]

    /**
     * Length of the polyline through the key centres of `sequence[from until to]`; NaN when a key is
     * missing from this layout.
     */
    fun pathLength(sequence: ByteArray, from: Int = 0, to: Int = sequence.size): Float {
        var total = 0f
        for (i in from + 1 until to) {
            total += distance(sequence[i - 1].toInt(), sequence[i].toInt())
        }
        return total
    }

    /**
     * True when the first and last key of [sequence] are within [radius] of the first and last of the
     * [count] points of [path] (interleaved coordinates).
     */
    fun endsNear(sequence: ByteArray, path: FloatArray, count: Int, radius: Float): Boolean {
        val first = sequence.first().toInt()
        val last = sequence.last().toInt()
        return has(first) && has(last) &&
            hypot(centerX[first] - path[0], centerY[first] - path[1]) <= radius &&
            hypot(centerX[last] - path[2 * count - 2], centerY[last] - path[2 * count - 1]) <=
            radius
    }

    /** The indices of the keys whose centre is within [radius] pixels of the point, nearest first. */
    fun keysNear(x: Float, y: Float, radius: Float): IntArray {
        val found = ArrayList<Pair<Int, Float>>()
        for (i in 0 until GestureAlphabet.size) {
            if (byIndex[i] == null) continue
            val d = hypot(centerX[i] - x, centerY[i] - y)
            if (d <= radius) found += i to d
        }
        if (found.isEmpty()) nearest(x, y)?.let { found += it to 0f }
        return found.sortedBy { it.second }.map { it.first }.toIntArray()
    }

    private fun nearest(x: Float, y: Float): Int? =
        (0 until GestureAlphabet.size).filter { byIndex[it] != null }
            .minByOrNull { hypot(centerX[it] - x, centerY[it] - y) }
}
