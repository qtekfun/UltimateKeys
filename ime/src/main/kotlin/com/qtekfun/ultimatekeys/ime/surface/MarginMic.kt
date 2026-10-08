// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

/**
 * The dictation button in the margin under the keys, at the right edge (pixels). Shared by the
 * renderer, the touch handling and the accessibility tree so they always agree.
 */
data class MarginMic(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** The icon is as tall as half the margin. */
    val iconSize: Float get() = (bottom - top) * ICON_RATIO

    fun contains(x: Float, y: Float): Boolean = containsX(x) && y >= top && y <= bottom

    fun containsX(x: Float): Boolean = x >= left && x <= right

    companion object {
        private const val ICON_RATIO = 0.5f
        private const val WIDTH_RATIO = 1.6f

        /** The zone below the keys, or null when there is no margin to put it in. */
        fun of(width: Float, keysBottom: Float, totalHeight: Float): MarginMic? {
            val height = totalHeight - keysBottom
            if (height <= 0f || width <= 0f) return null
            val zoneWidth = minOf(width, height * WIDTH_RATIO)
            return MarginMic(width - zoneWidth, keysBottom, width, totalHeight)
        }
    }
}
