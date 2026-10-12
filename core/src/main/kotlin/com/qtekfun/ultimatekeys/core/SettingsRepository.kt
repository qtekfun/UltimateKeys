// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface SettingsRepository {
    /** The settings as they apply to the screen in use (see [screenProfile]). */
    val settings: Flow<KeyboardSettings>

    suspend fun update(transform: (KeyboardSettings) -> KeyboardSettings)

    /** The profile of the screen in use; always [ScreenProfile.NORMAL] unless the device folds. */
    val screenProfile: Flow<ScreenProfile> get() = flowOf(ScreenProfile.NORMAL)

    /** The settings as they apply to [profile], whichever screen is in use now. */
    fun settingsOf(profile: ScreenProfile): Flow<KeyboardSettings> = settings

    /** Changes the settings of [profile]; the size-related ones are then kept for that screen only. */
    suspend fun updateOf(
        profile: ScreenProfile,
        transform: (KeyboardSettings) -> KeyboardSettings
    ) = update(transform)
}
