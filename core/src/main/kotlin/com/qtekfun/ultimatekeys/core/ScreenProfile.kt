// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

/**
 * Which screen the keyboard is drawn on, for the settings that depend on the size of the screen (heights,
 * margins, width, split, sideways microphone).
 *
 * [NORMAL] is every phone and tablet that does not fold; its values are the original, un-prefixed settings,
 * and they are also what a foldable's screen starts with until the user changes it there.
 */
enum class ScreenProfile {
    NORMAL,

    /** The large screen of a foldable, while it is unfolded. */
    FOLDABLE_INNER,

    /** The small screen of a foldable, the one used while it is folded. */
    FOLDABLE_OUTER
}

/** The decision of which [ScreenProfile] a screen gets; pure, so that it is testable without a device. */
object ScreenProfiles {
    /** A foldable's screen at least this wide (smallest width, in dp) is its large, unfolded one. */
    const val INNER_MIN_SMALLEST_WIDTH_DP = 600

    /**
     * [isFoldableDevice] is whether the hardware folds; [smallestWidthDp] is the smallest width of the screen the
     * keyboard is on. A device that does not fold always gets [ScreenProfile.NORMAL], whatever its size.
     */
    fun decide(isFoldableDevice: Boolean, smallestWidthDp: Int): ScreenProfile = when {
        !isFoldableDevice -> ScreenProfile.NORMAL
        smallestWidthDp >= INNER_MIN_SMALLEST_WIDTH_DP -> ScreenProfile.FOLDABLE_INNER
        else -> ScreenProfile.FOLDABLE_OUTER
    }
}
