// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.layouts.KeyboardLayout
import com.qtekfun.ultimatekeys.layouts.LayoutKey

/** A key placed on the surface; coordinates are in pixels. */
data class PlacedKey(
    val key: LayoutKey,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun distanceSquaredTo(x: Float, y: Float): Float {
        val dx = maxOf(left - x, 0f, x - right)
        val dy = maxOf(top - y, 0f, y - bottom)
        return dx * dx + dy * dy
    }
}

/**
 * Pure key placement and hit testing, with no Android types so it can be unit tested.
 *
 * Keys are laid out in rows of equal height; widths inside a row are proportional to the key
 * weights. [gapX]/[gapY] are the visual gaps; touches that land in a gap or just outside a key are
 * resolved to the nearest key, which is what makes fast typing forgiving.
 */
class KeyGeometry(
    layout: KeyboardLayout,
    val width: Float,
    val rowHeight: Float,
    gapX: Float = 0f,
    gapY: Float = 0f,
    val top: Float = 0f,
    /** Optional height multiplier per row (1 = [rowHeight]); missing entries mean 1. */
    rowScales: List<Float> = emptyList()
) {
    val keys: List<PlacedKey>
    val height: Float = layout.rows.indices.sumOf {
        (rowHeight * rowScales.getOrElse(it) { 1f }).toDouble()
    }.toFloat()

    init {
        val placed = ArrayList<PlacedKey>()
        var rowTop = top
        layout.rows.forEachIndexed { rowIndex, row ->
            val thisRowHeight = rowHeight * rowScales.getOrElse(rowIndex) { 1f }
            val totalWeight = row.keys.sumOf { it.width.toDouble() }.toFloat()
            val available = width - gapX * row.keys.size
            var x = gapX / 2f
            row.keys.forEach { key ->
                val w = available * key.width / totalWeight
                placed +=
                    PlacedKey(key, x, rowTop + gapY / 2f, x + w, rowTop + thisRowHeight - gapY / 2f)
                x += w + gapX
            }
            rowTop += thisRowHeight
        }
        keys = placed
    }

    /** The key a touch at ([x], [y]) means, or null when the surface has no keys. */
    fun keyAt(x: Float, y: Float): PlacedKey? {
        if (keys.isEmpty()) return null
        var best: PlacedKey? = null
        var bestDistance = Float.MAX_VALUE
        for (candidate in keys) {
            val d = candidate.distanceSquaredTo(x, y)
            val better = d < bestDistance ||
                (d == bestDistance && best != null && closerCenter(candidate, best, x))
            if (better) {
                best = candidate
                bestDistance = d
            }
        }
        return best
    }

    private fun closerCenter(a: PlacedKey, b: PlacedKey, x: Float) =
        kotlin.math.abs(a.centerX - x) < kotlin.math.abs(b.centerX - x)
}
