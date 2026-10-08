// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ui

/** How the large title of a screen hands over to the small one in the bar as the list scrolls. */
object TitleCollapse {
    /** Share of the large title that must scroll away before the bar title is fully shown. */
    private const val COLLAPSE_SHARE = 0.6f

    /** 0 while the large title is fully in view, 1 once it has scrolled under the bar. */
    fun fraction(scrollPx: Float, titleHeightPx: Float): Float {
        if (titleHeightPx <= 0f) return 0f
        return (scrollPx / (titleHeightPx * COLLAPSE_SHARE)).coerceIn(0f, 1f)
    }

    /** The large title fades out as the collapse starts... */
    fun largeAlpha(fraction: Float): Float = 1f - fraction.coerceIn(0f, 1f)

    /** ...and the bar title fades in during the second half of it. */
    fun smallAlpha(fraction: Float): Float = ((fraction - HALF) / HALF).coerceIn(0f, 1f)

    /** The hairline under the bar shows once content slides below it. */
    fun separatorAlpha(fraction: Float): Float = fraction.coerceIn(0f, 1f)

    private const val HALF = 0.5f
}

/** The two ways to show a short list of options in a settings row. */
enum class ChoicePresentation {
    /** All options side by side in one control. */
    Segmented,

    /** A row showing the current option; tapping it opens a menu of all of them. */
    Picker;

    companion object {
        private const val MAX_SEGMENTS = 4
        private const val MAX_LABEL_CHARS = 14
        private const val MAX_TOTAL_CHARS = 30

        /**
         * Two to four short labels fit as segments (also at a large font, where a segment wraps
         * its text); anything longer or more numerous is a picker.
         */
        fun of(labels: List<String>): ChoicePresentation {
            val fits = labels.size in 2..MAX_SEGMENTS &&
                labels.all { it.length <= MAX_LABEL_CHARS } &&
                labels.sumOf { it.length } <= MAX_TOTAL_CHARS
            return if (fits) Segmented else Picker
        }
    }
}
