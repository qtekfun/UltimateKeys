// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qtekfun.ultimatekeys.style.Presets
import com.qtekfun.ultimatekeys.style.Style
import com.qtekfun.ultimatekeys.style.StyleCodec
import com.qtekfun.ultimatekeys.style.StyleRepository
import com.qtekfun.ultimatekeys.style.resolveActive
import com.qtekfun.ultimatekeys.style.uniqueCustom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Keeps the selected style id and the user's styles in the app's preferences DataStore. */
class DataStoreStyleRepository(private val store: DataStore<Preferences>) : StyleRepository {
    override val activeId: Flow<String> =
        store.data.map { it[ACTIVE] ?: Presets.default.id }.distinctUntilChanged()

    override val custom: Flow<List<Style>> =
        store.data.map { prefs -> prefs[CUSTOM]?.let(StyleCodec::decodeAll).orEmpty() }
            .distinctUntilChanged()

    override val active: Flow<Style> = store.data.map { prefs ->
        resolveActive(
            prefs[ACTIVE] ?: Presets.default.id,
            prefs[CUSTOM]?.let(StyleCodec::decodeAll).orEmpty()
        )
    }.distinctUntilChanged()

    override suspend fun select(id: String) {
        store.edit { it[ACTIVE] = id }
    }

    override suspend fun save(style: Style): Style {
        var saved = style
        store.edit { prefs ->
            val list = prefs[CUSTOM]?.let(StyleCodec::decodeAll).orEmpty()
            saved = uniqueCustom(style, list)
            prefs[CUSTOM] = StyleCodec.encodeAll(list.filterNot { it.id == saved.id } + saved)
        }
        return saved
    }

    override suspend fun delete(id: String) {
        store.edit { prefs ->
            val list = prefs[CUSTOM]?.let(StyleCodec::decodeAll).orEmpty()
            prefs[CUSTOM] = StyleCodec.encodeAll(list.filterNot { it.id == id })
            if (prefs[ACTIVE] == id) prefs[ACTIVE] = Presets.default.id
        }
    }

    private companion object {
        val ACTIVE = stringPreferencesKey("active_style_id")
        val CUSTOM = stringPreferencesKey("custom_styles")
    }
}
