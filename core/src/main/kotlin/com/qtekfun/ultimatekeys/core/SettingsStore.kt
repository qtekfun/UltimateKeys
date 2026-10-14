// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private val Context.keyboardSettingsStore by preferencesDataStore(name = "keyboard_settings")

/** The user's active style and custom styles, in the same DataStore file as the settings. */
fun Context.styleRepository(): com.qtekfun.ultimatekeys.style.StyleRepository =
    DataStoreStyleRepository(applicationContext.keyboardSettingsStore)

/** The settings repository backed by the app's single DataStore file. */
fun Context.settingsRepository(): SettingsRepository {
    ScreenProfileTracker.refresh(this)
    return DataStoreSettingsRepository(
        applicationContext.keyboardSettingsStore,
        ScreenProfileTracker.profile
    )
}

/** Backups of the settings, styles and the user dictionary. */
fun Context.settingsBackup(): SettingsBackup =
    SettingsBackup(applicationContext.keyboardSettingsStore, userWordsRepository())

private object UserWordsHolder {
    @Volatile
    private var repository: UserWordsRepository? = null

    fun get(context: Context): UserWordsRepository = repository ?: synchronized(this) {
        repository ?: UserWordsRepository(
            java.io.File(context.applicationContext.filesDir, "user_words.txt")
        ).also { repository = it }
    }
}

/** The user's own words (one repository per process). */
fun Context.userWordsRepository(): UserWordsRepository = UserWordsHolder.get(this)
