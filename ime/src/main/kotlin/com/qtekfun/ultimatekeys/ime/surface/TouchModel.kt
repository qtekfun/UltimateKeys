// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

/** What a finger is doing; drives how a drag is interpreted. */
enum class PressMode { NORMAL, CURSOR, DELETE_SELECT, GESTURE }

/** The long-press alternatives strip, positioned in surface pixels. */
data class ChooserView(
    val items: List<String>,
    val left: Float,
    val top: Float,
    val cellWidth: Float,
    val cellHeight: Float,
    val selected: Int
) {
    val right: Float get() = left + cellWidth * items.size

    fun indexAt(x: Float): Int = ((x - left) / cellWidth).toInt().coerceIn(0, items.lastIndex)
}

/** Immutable snapshot of one active press, read by the renderer. */
data class PressView(
    val key: PlacedKey,
    val mode: PressMode = PressMode.NORMAL,
    val chooser: ChooserView? = null,
    /** The last points of a gesture's path, interleaved `[x0, y0, x1, y1, ...]`; empty when none is drawn. */
    val trail: FloatArray = FloatArray(0)
)
