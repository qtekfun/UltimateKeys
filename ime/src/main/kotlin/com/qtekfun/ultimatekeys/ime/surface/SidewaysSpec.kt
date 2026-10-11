// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.ime.BottomRowFeatures
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.style.MicPlacement
import com.qtekfun.ultimatekeys.style.Style

/**
 * How the surface adapts to the way the phone is held and to the split option: a compact strip, no bottom
 * margin, the microphone setting, the key height and width, and the free space between split halves.
 */
object SidewaysSpec {
    private const val PERCENT = 100f

    /** Sideways, the keyboard may take at most this share of the screen height. */
    const val MAX_LANDSCAPE_SHARE = 0.6f

    /** Sideways the suggestion strip is at most this tall: every dp counts on a screen this short. */
    const val LANDSCAPE_BAR_DP = 36

    /**
     * The style as it is used on this screen. Held sideways the room is short, so the strip is compact, the
     * bottom margin is dropped (see [sizing]) and the microphone, when it is shown, sits in the strip.
     */
    fun styleFor(style: Style, landscape: Boolean): Style {
        if (!landscape) return style
        // Sideways the microphone has its own setting: it is hidden or it sits in the strip, never elsewhere.
        val mic = MicPlacement.SUGGESTION_BAR
        return style.copy(
            suggestionBar = style.suggestionBar.copy(
                heightDp = minOf(style.suggestionBar.heightDp, LANDSCAPE_BAR_DP)
            ),
            bottomRow = style.bottomRow.copy(micPlacement = mic)
        )
    }

    /** The settings as the surface sizes itself with them: no bottom margin sideways. */
    fun sizing(settings: KeyboardSettings, landscape: Boolean): KeyboardSettings =
        if (landscape) settings.copy(bottomMarginDp = 0) else settings

    /** Free space added at each side of the keys so that they take [percent] of the width, centered. */
    fun extraSidePx(widthPx: Float, landscape: Boolean, percent: Int): Float =
        if (landscape) widthPx * (PERCENT - percent) / (2f * PERCENT) else 0f

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
        val total = SurfaceSpec.totalHeightDp(
            rows,
            requested,
            margin,
            style,
            settings.numberRow,
            micInMargin
        )
        if (total <= maxTotalDp) return requested
        val fixed =
            style.suggestionBar.heightDp + SurfaceSpec.BOTTOM_PADDING_DP +
                SurfaceSpec.marginDp(margin, micInMargin)
        val scales = SurfaceSpec.rowScales(style, rows, settings.numberRow).sum()
        val rowDp = (maxTotalDp - fixed) / scales
        return (rowDp * PERCENT / SurfaceSpec.BASE_ROW_DP).toInt().coerceIn(
            SurfaceSpec.MIN_PERCENT,
            requested
        )
    }

    /** Whether the keys of [page] are split in two halves now. Number and phone pads never are. */
    fun isSplit(settings: KeyboardSettings, landscape: Boolean, page: Page): Boolean {
        if (page == Page.NUMERIC || page == Page.PHONE) return false
        return when (settings.splitMode) {
            KeyboardSettings.SPLIT_ALWAYS -> true
            KeyboardSettings.SPLIT_LANDSCAPE -> landscape
            KeyboardSettings.SPLIT_PORTRAIT -> !landscape
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

    /** The optional keys that exist now: held sideways the microphone follows its own setting. */
    fun featuresFor(
        features: BottomRowFeatures,
        settings: KeyboardSettings,
        landscape: Boolean
    ): BottomRowFeatures {
        val hidden = landscape && settings.landscapeMic != KeyboardSettings.LANDSCAPE_MIC_STRIP
        return if (hidden) features.copy(voice = false) else features
    }
}
