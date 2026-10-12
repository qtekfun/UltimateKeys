// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * The DataStore keys of the size-related settings of one foldable screen. They are separate from the original
 * keys, which belong to [ScreenProfile.NORMAL]: a key that was never written falls back to the original value,
 * so a foldable starts with what the user had and nothing is migrated or lost.
 */
internal class ScreenProfileKeys private constructor(prefix: String) {
    private val height = intPreferencesKey("${prefix}height_percent")
    private val landscapeHeight = intPreferencesKey("${prefix}landscape_height_percent_v2")
    private val landscapeWidth = intPreferencesKey("${prefix}landscape_width_percent_v2")
    private val landscapeMic = stringPreferencesKey("${prefix}landscape_mic")
    private val splitMode = stringPreferencesKey("${prefix}split_mode")
    private val splitGap = intPreferencesKey("${prefix}split_gap_percent")
    private val bottomMargin = intPreferencesKey("${prefix}bottom_margin_dp")
    private val sideMargin = intPreferencesKey("${prefix}side_margin_dp")

    /** [base] with this screen's own values where it has any. */
    fun read(prefs: Preferences, base: KeyboardSettings) = base.copy(
        heightPercent = prefs[height] ?: base.heightPercent,
        landscapeHeightPercent = prefs[landscapeHeight] ?: base.landscapeHeightPercent,
        landscapeWidthPercent = prefs[landscapeWidth] ?: base.landscapeWidthPercent,
        landscapeMic = prefs[landscapeMic] ?: base.landscapeMic,
        splitMode = prefs[splitMode] ?: base.splitMode,
        splitGapPercent = prefs[splitGap] ?: base.splitGapPercent,
        bottomMarginDp = prefs[bottomMargin] ?: base.bottomMarginDp,
        sideMarginDp = prefs[sideMargin] ?: base.sideMarginDp
    )

    /** Stores only what [after] changed from [before], so the rest keeps following the fallback. */
    fun write(prefs: MutablePreferences, before: KeyboardSettings, after: KeyboardSettings) {
        prefs.putIfChanged(height, before.heightPercent, after.heightPercent)
        prefs.putIfChanged(
            landscapeHeight,
            before.landscapeHeightPercent,
            after.landscapeHeightPercent
        )
        prefs.putIfChanged(
            landscapeWidth,
            before.landscapeWidthPercent,
            after.landscapeWidthPercent
        )
        prefs.putIfChanged(landscapeMic, before.landscapeMic, after.landscapeMic)
        prefs.putIfChanged(splitMode, before.splitMode, after.splitMode)
        prefs.putIfChanged(splitGap, before.splitGapPercent, after.splitGapPercent)
        prefs.putIfChanged(bottomMargin, before.bottomMarginDp, after.bottomMarginDp)
        prefs.putIfChanged(sideMargin, before.sideMarginDp, after.sideMarginDp)
    }

    private fun <T> MutablePreferences.putIfChanged(key: Preferences.Key<T>, before: T, after: T) {
        if (before != after) this[key] = after
    }

    companion object {
        private val inner = ScreenProfileKeys("fold_inner_")
        private val outer = ScreenProfileKeys("fold_outer_")

        /** The keys of [profile], or null for [ScreenProfile.NORMAL], which uses the original ones. */
        fun of(profile: ScreenProfile): ScreenProfileKeys? = when (profile) {
            ScreenProfile.NORMAL -> null
            ScreenProfile.FOLDABLE_INNER -> inner
            ScreenProfile.FOLDABLE_OUTER -> outer
        }
    }
}

/** [this] with the layout (size-related) fields of [other]. */
internal fun KeyboardSettings.withLayoutOf(other: KeyboardSettings) = copy(
    heightPercent = other.heightPercent,
    landscapeHeightPercent = other.landscapeHeightPercent,
    landscapeWidthPercent = other.landscapeWidthPercent,
    landscapeMic = other.landscapeMic,
    splitMode = other.splitMode,
    splitGapPercent = other.splitGapPercent,
    bottomMarginDp = other.bottomMarginDp,
    sideMarginDp = other.sideMarginDp
)
