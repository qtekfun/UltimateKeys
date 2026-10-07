// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<KeyboardSettings>

    suspend fun update(transform: (KeyboardSettings) -> KeyboardSettings)
}
