// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.layouts.KeyboardLayout
import com.qtekfun.ultimatekeys.style.Presets
import com.qtekfun.ultimatekeys.style.Style

/** Dimensions of the surface in dp, shared by the renderer and by tests that tap real keys. */
object SurfaceSpec {
    const val BASE_ROW_DP = 54f
    const val BOTTOM_PADDING_DP = 6f

    /** Keys are never squeezed below this height percent, whatever the room. */
    const val MIN_PERCENT = 40

    /** Sideways, the keyboard may take at most this share of the screen height. */
    const val MAX_LANDSCAPE_SHARE = 0.6f

    /** The bottom margin is at least this tall when the microphone lives in it. */
    const val MIC_MARGIN_DP = 40f
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

    /** True when dictation exists and the style puts its button in the bottom margin. */
    fun micInMargin(voice: Boolean, style: Style): Boolean = voice &&
        style.bottomRow.micPlacement ==
        com.qtekfun.ultimatekeys.style.MicPlacement.BOTTOM_MARGIN

    /** The margin under the keys, made tall enough for the microphone when it sits there. */
    fun marginDp(bottomMarginDp: Int, micInMargin: Boolean): Float = if (micInMargin) {
        maxOf(
            bottomMarginDp.toFloat(),
            MIC_MARGIN_DP
        )
    } else {
        bottomMarginDp.toFloat()
    }

    fun totalHeightDp(
        rows: Int,
        heightPercent: Int,
        bottomMarginDp: Int = 0,
        style: Style = Presets.default,
        numberRow: Boolean = false,
        micInMargin: Boolean = false
    ): Float = style.suggestionBar.heightDp +
        rowDp(heightPercent) * rowScales(style, rows, numberRow).sum() +
        BOTTOM_PADDING_DP + marginDp(bottomMarginDp, micInMargin)

    /** What the surface needs to know about the screen it is on. */
    data class Screen(val landscape: Boolean, val heightDp: Int)

    /**
     * The key height percent to really use: the one the user chose for this orientation, lowered when the
     * keyboard would be taller than its share of a sideways screen (where it would cover everything).
     */
    fun effectiveHeightPercent(
        rows: Int,
        style: Style,
        settings: KeyboardSettings,
        micInMargin: Boolean,
        screen: Screen
    ): Int {
        if (!screen.landscape) return settings.heightPercent
        val requested = settings.landscapeHeightPercent
        val maxTotalDp = screen.heightDp * MAX_LANDSCAPE_SHARE
        val margin = settings.bottomMarginDp
        val total = totalHeightDp(rows, requested, margin, style, settings.numberRow, micInMargin)
        if (total <= maxTotalDp) return requested
        val fixed = style.suggestionBar.heightDp + BOTTOM_PADDING_DP + marginDp(margin, micInMargin)
        val scales = rowScales(style, rows, settings.numberRow).sum()
        val rowDp = (maxTotalDp - fixed) / scales
        return (rowDp * PERCENT / BASE_ROW_DP).toInt().coerceIn(MIN_PERCENT, requested)
    }

    /** Whether the keys of [page] are split in two halves now. Number and phone pads never are. */
    fun isSplit(settings: KeyboardSettings, landscape: Boolean, page: Page): Boolean {
        if (page == Page.NUMERIC || page == Page.PHONE) return false
        return when (settings.splitMode) {
            KeyboardSettings.SPLIT_ALWAYS -> true
            KeyboardSettings.SPLIT_LANDSCAPE -> landscape
            else -> false
        }
    }

    /** The free space between the two halves in pixels; 0 when the keys of [page] are not split now. */
    fun splitGapPx(
        settings: KeyboardSettings,
        landscape: Boolean,
        page: Page,
        widthPx: Float
    ): Float =
        if (isSplit(settings, landscape, page)) widthPx * settings.splitGapPercent / PERCENT else 0f

    fun geometry(
        layout: KeyboardLayout,
        widthPx: Float,
        density: Float,
        heightPercent: Int,
        style: Style = Presets.default,
        numberRow: Boolean = false,
        sideMarginDp: Int = 0,
        edgeBoostPercent: Int = 0,
        splitGapPx: Float = 0f
    ) = KeyGeometry(
        layout,
        width = widthPx,
        rowHeight = rowDp(heightPercent) * density,
        gapX = style.keys.gapXDp * density,
        gapY = style.keys.gapYDp * density,
        top = style.suggestionBar.heightDp * density,
        rowScales = rowScales(style, layout.rows.size, numberRow),
        sideInset = sideMarginDp * density,
        edgeBoost = edgeBoostPercent / PERCENT,
        splitGap = splitGapPx
    )
}
