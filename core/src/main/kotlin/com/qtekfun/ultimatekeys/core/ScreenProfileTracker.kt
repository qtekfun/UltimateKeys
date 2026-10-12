// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The [ScreenProfile] of the screen in use, kept for the whole process so the keyboard and the settings agree.
 * Call [refresh] whenever the configuration may have changed (a fold or an unfold changes it).
 *
 * Only the platform is used: the hinge angle sensor feature says that the hardware folds and the smallest
 * width of the configuration says which of its screens is in use.
 */
object ScreenProfileTracker {
    private const val FEATURE_HINGE_ANGLE = "android.hardware.sensor.hinge_angle"

    private val state = MutableStateFlow(ScreenProfile.NORMAL)

    /** The profile of the screen in use. */
    val profile: StateFlow<ScreenProfile> = state.asStateFlow()

    /** Whether the hardware of this device folds. */
    fun isFoldableDevice(context: Context): Boolean =
        context.packageManager.hasSystemFeature(FEATURE_HINGE_ANGLE)

    /** Reads the current screen of [context] and returns (and publishes) its profile. */
    fun refresh(context: Context): ScreenProfile {
        val width = context.resources.configuration.smallestScreenWidthDp
        val next = ScreenProfiles.decide(isFoldableDevice(context), width)
        state.value = next
        return next
    }
}
