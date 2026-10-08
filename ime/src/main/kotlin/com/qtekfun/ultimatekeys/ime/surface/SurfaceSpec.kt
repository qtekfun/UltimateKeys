// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.layouts.KeyboardLayout
import com.qtekfun.ultimatekeys.style.Presets
import com.qtekfun.ultimatekeys.style.Style

/** Dimensions of the surface in dp, shared by the renderer and by tests that tap real keys. */
object SurfaceSpec {
    const val BASE_ROW_DP = 54f
    const val BOTTOM_PADDING_DP = 6f
    private const val PERCENT = 100f

    fun rowDp(heightPercent: Int): Float = BASE_ROW_DP * heightPercent / PERCENT

    /** Height multiplier of each row: the number row, the bottom row and the letter rows in between. */
    fun rowScales(style: Style, rows: Int, numberRow: Boolean): List<Float> = List(rows) { index ->
        val percent = when {
            numberRow && index == 0 -> style.keys.numberRowHeightPercent
            index == rows - 1 && rows > 1 -> style.keys.bottomRowHeightPercent
            else -> style.keys.letterRowHeightPercent
        }
        percent / PERCENT
    }

    fun totalHeightDp(
        rows: Int,
        heightPercent: Int,
        bottomMarginDp: Int = 0,
        style: Style = Presets.default,
        numberRow: Boolean = false
    ): Float = style.suggestionBar.heightDp +
        rowDp(heightPercent) * rowScales(style, rows, numberRow).sum() +
        BOTTOM_PADDING_DP + bottomMarginDp

    fun geometry(
        layout: KeyboardLayout,
        widthPx: Float,
        density: Float,
        heightPercent: Int,
        style: Style = Presets.default,
        numberRow: Boolean = false
    ) = KeyGeometry(
        layout,
        width = widthPx,
        rowHeight = rowDp(heightPercent) * density,
        gapX = style.keys.gapXDp * density,
        gapY = style.keys.gapYDp * density,
        top = style.suggestionBar.heightDp * density,
        rowScales = rowScales(style, layout.rows.size, numberRow)
    )
}
