// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private val Context.keyboardSettingsStore by preferencesDataStore(name = "keyboard_settings")

/** The settings repository backed by the app's single DataStore file. */
fun Context.settingsRepository(): SettingsRepository =
    DataStoreSettingsRepository(applicationContext.keyboardSettingsStore)
