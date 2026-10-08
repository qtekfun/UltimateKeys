// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

/**
 * The dictation button in the margin under the keys, at the left or right edge (pixels). It sits
 * on the left by default because the system's own keyboard switcher button uses the right.
 * Shared by the renderer, the touch handling and the accessibility tree.
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

        /** [of] when [enabled], otherwise null. */
        fun forSurface(
            enabled: Boolean,
            width: Float,
            keysBottom: Float,
            totalHeight: Float,
            onLeft: Boolean = true
        ): MarginMic? = if (enabled) of(width, keysBottom, totalHeight, onLeft) else null

        /** The zone below the keys, or null when there is no margin to put it in. */
        fun of(
            width: Float,
            keysBottom: Float,
            totalHeight: Float,
            onLeft: Boolean = true
        ): MarginMic? {
            val height = totalHeight - keysBottom
            if (height <= 0f || width <= 0f) return null
            val zoneWidth = minOf(width, height * WIDTH_RATIO)
            return if (onLeft) {
                MarginMic(0f, keysBottom, zoneWidth, totalHeight)
            } else {
                MarginMic(width - zoneWidth, keysBottom, width, totalHeight)
            }
        }
    }
}
