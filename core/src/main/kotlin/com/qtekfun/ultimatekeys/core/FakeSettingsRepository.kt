// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [SettingsRepository] for tests and previews. */
class FakeSettingsRepository(initial: KeyboardSettings = KeyboardSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings = state.asStateFlow()

    override suspend fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        state.value = transform(state.value).sanitized()
    }
}
