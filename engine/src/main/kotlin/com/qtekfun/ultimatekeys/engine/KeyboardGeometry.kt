// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

import java.util.Locale
import kotlin.math.max

/** One letter key in abstract layout units; [codePoint] must be lower case. */
data class KeyRect(val codePoint: Int, val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Key positions the native engine uses to find neighbouring keys, so that a slip on an adjacent key
 * ("hwllo") is still corrected. Units are arbitrary but consistent (the engine only uses ratios);
 * it does not need to match the pixel layout of the keyboard on screen.
 */
data class KeyboardGeometry(val width: Int, val height: Int, val keys: List<KeyRect>) {
    init {
        require(width > 0 && height > 0) { "Empty keyboard area" }
        require(keys.isNotEmpty()) { "A geometry needs at least one key" }
    }

    /** Width of the most common key, used by the engine to scale distances. */
    val mostCommonKeyWidth: Int get() = keys.groupingBy {
        it.width
    }.eachCount().maxBy { it.value }.key

    val mostCommonKeyHeight: Int get() = keys.groupingBy {
        it.height
    }.eachCount().maxBy { it.value }.key

    /**
     * For every grid cell, the code points of the keys near it, nearest first, padded with
     * [NOT_A_CODE_POINT]: `GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS` entries, row-major, the
     * layout the native `ProximityInfo` expects.
     */
    fun proximityChars(): IntArray {
        val cellWidth = (width + GRID_WIDTH - 1) / GRID_WIDTH
        val cellHeight = (height + GRID_HEIGHT - 1) / GRID_HEIGHT
        val threshold = SEARCH_DISTANCE * mostCommonKeyWidth
        val thresholdSquared = threshold * threshold
        val out = IntArray(GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS) { NOT_A_CODE_POINT }
        for (row in 0 until GRID_HEIGHT) {
            for (column in 0 until GRID_WIDTH) {
                val centerX = column * cellWidth + cellWidth / 2
                val centerY = row * cellHeight + cellHeight / 2
                val nearest = keys
                    .map { it to it.squaredDistanceTo(centerX, centerY) }
                    .filter { it.second < thresholdSquared }
                    .sortedBy { it.second }
                    .take(MAX_PROXIMITY_CHARS)
                val start = (row * GRID_WIDTH + column) * MAX_PROXIMITY_CHARS
                nearest.forEachIndexed { index, (key, _) -> out[start + index] = key.codePoint }
            }
        }
        return out
    }

    companion object {
        const val GRID_WIDTH = 32
        const val GRID_HEIGHT = 16
        const val MAX_PROXIMITY_CHARS = 16
        const val NOT_A_CODE_POINT = -1
        private const val SEARCH_DISTANCE = 1.2f
        private const val ROW_UNIT_WIDTH = 100
        private const val ROW_UNIT_HEIGHT = 150

        /**
         * Builds a staggered geometry from rows of lower-case letters, each row centred like a
         * physical QWERTY block: `fromRows(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"))`.
         */
        fun fromRows(rows: List<String>): KeyboardGeometry {
            val widest = rows.maxOf { it.codePointCount(0, it.length) }
            val width = max(widest, 1) * ROW_UNIT_WIDTH
            val keys = rows.flatMapIndexed { rowIndex, row ->
                val codePoints = row.codePoints().toArray()
                val offset = (width - codePoints.size * ROW_UNIT_WIDTH) / 2
                codePoints.mapIndexed { column, codePoint ->
                    KeyRect(
                        codePoint = codePoint,
                        x = offset + column * ROW_UNIT_WIDTH,
                        y = rowIndex * ROW_UNIT_HEIGHT,
                        width = ROW_UNIT_WIDTH,
                        height = ROW_UNIT_HEIGHT
                    )
                }
            }
            return KeyboardGeometry(width, rows.size * ROW_UNIT_HEIGHT, keys)
        }

        private val QWERTY_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        private val SPANISH_ROWS = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")

        /** A QWERTY geometry for [locale]: Spanish gets the `ñ` key, everything else plain QWERTY. */
        fun qwertyFor(locale: Locale): KeyboardGeometry = fromRows(
            if (locale.language == "es") SPANISH_ROWS else QWERTY_ROWS
        )
    }
}

private fun KeyRect.squaredDistanceTo(px: Int, py: Int): Long {
    val dx = max(max(x - px, px - (x + width)), 0).toLong()
    val dy = max(max(y - py, py - (y + height)), 0).toLong()
    return dx * dx + dy * dy
}
