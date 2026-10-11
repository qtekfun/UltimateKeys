// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.layouts.CharKey
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
    rowScales: List<Float> = emptyList(),
    /** Free space between the screen edge and the keys; touches there still reach the edge keys. */
    sideInset: Float = 0f,
    /** Extra width (0.2 = 20%) of a letter at either end of a row, to make it easier to hit. */
    edgeBoost: Float = 0f,
    /** Empty space in the middle of every row that has enough keys, for a split keyboard (0 = one block). */
    splitGap: Float = 0f
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
            val items = rowItems(row.keys, edgeBoost)
            val splitAt = if (splitGap > 0f &&
                row.keys.size >= MIN_SPLIT_KEYS
            ) {
                splitIndex(items)
            } else {
                -1
            }
            val totalWeight = items.sumOf { it.weight.toDouble() }.toFloat()
            val rowGap = if (splitAt >= 0) splitGap else 0f
            val available = width - 2f * sideInset - gapX * items.size - rowGap
            var x = sideInset + gapX / 2f
            items.forEachIndexed { i, item ->
                if (i == splitAt) x += rowGap
                val w = available * item.weight / totalWeight
                placed += PlacedKey(
                    item.key,
                    x,
                    rowTop + gapY / 2f,
                    x + w,
                    rowTop + thisRowHeight - gapY / 2f
                )
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

    private class Item(val key: LayoutKey, val weight: Float)

    private fun rowItems(keys: List<LayoutKey>, edgeBoost: Float): MutableList<Item> =
        keys.mapIndexedTo(ArrayList()) { i, key ->
            val atEnd = i == 0 || i == keys.lastIndex
            Item(key, key.width * if (atEnd && key is CharKey) 1f + edgeBoost else 1f)
        }

    /**
     * The index of the first item of the right half: the keys left of the middle go left and the ones right
     * of it go right. A long key that crosses the middle (the space bar) is cut in two, one piece per half,
     * so neither thumb is left without it.
     */
    private fun splitIndex(items: MutableList<Item>): Int {
        val half = items.sumOf { it.weight.toDouble() }.toFloat() / 2f
        var passed = 0f
        for (i in items.indices) {
            val w = items[i].weight
            val crosses = passed < half - EPSILON && passed + w > half + EPSILON
            if (crosses && w >= LONG_KEY_WEIGHT) {
                val key = items[i].key
                items[i] = Item(key, w / 2f)
                items.add(i + 1, Item(key, w / 2f))
                return i + 1
            }
            if (passed + w / 2f >= half) return i
            passed += w
        }
        return items.size
    }

    private fun closerCenter(a: PlacedKey, b: PlacedKey, x: Float) =
        kotlin.math.abs(a.centerX - x) < kotlin.math.abs(b.centerX - x)

    private companion object {
        /** Rows with fewer keys than this (a lone space bar, a phone pad row) are never split. */
        const val MIN_SPLIT_KEYS = 4
        const val LONG_KEY_WEIGHT = 2f
        const val EPSILON = 0.001f
    }
}
