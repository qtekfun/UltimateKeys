// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.layouts.KeyboardLayout

/** Dimensions of the surface in dp, shared by the renderer and by tests that tap real keys. */
object SurfaceSpec {
    const val BASE_ROW_DP = 54f
    const val STRIP_DP = 44f
    const val BOTTOM_PADDING_DP = 6f
    const val GAP_X_DP = 4f
    const val GAP_Y_DP = 6f
    private const val PERCENT = 100f

    fun rowDp(heightPercent: Int): Float = BASE_ROW_DP * heightPercent / PERCENT

    fun totalHeightDp(rows: Int, heightPercent: Int, bottomMarginDp: Int = 0): Float =
        STRIP_DP + rowDp(heightPercent) * rows + BOTTOM_PADDING_DP + bottomMarginDp

    fun geometry(layout: KeyboardLayout, widthPx: Float, density: Float, heightPercent: Int) =
        KeyGeometry(
            layout,
            width = widthPx,
            rowHeight = rowDp(heightPercent) * density,
            gapX = GAP_X_DP * density,
            gapY = GAP_Y_DP * density,
            top = STRIP_DP * density
        )
}
