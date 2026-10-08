// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** The active style and the user's own styles. Built-in presets are always available. */
interface StyleRepository {
    val activeId: Flow<String>

    /** The style the keyboard is drawn with: the selected one, or the default if it vanished. */
    val active: Flow<Style>

    /** The user's own styles (not the presets). */
    val custom: Flow<List<Style>>

    suspend fun select(id: String)

    /** Adds or replaces a custom style. A style whose id belongs to a preset gets a new id. */
    suspend fun save(style: Style): Style

    suspend fun delete(id: String)
}

/** Picks the active style from the ids and lists; shared by every repository implementation. */
fun resolveActive(id: String, custom: List<Style>): Style =
    custom.firstOrNull { it.id == id } ?: Presets.byId(id) ?: Presets.default

/** Gives [style] an id that is free among presets and [existing] styles (the caller's own id if possible). */
fun uniqueCustom(style: Style, existing: List<Style>): Style {
    val clean = style.sanitized()
    if (!Presets.isPreset(clean.id)) return clean
    var n = 1
    val taken = existing.map { it.id }.toSet()
    while ("${clean.id}-$n" in taken || Presets.isPreset("${clean.id}-$n")) n++
    return clean.copy(id = "${clean.id}-$n")
}

class InMemoryStyleRepository(initialActive: String = Presets.default.id) : StyleRepository {
    private val activeIdState = MutableStateFlow(initialActive)
    private val customState = MutableStateFlow(emptyList<Style>())

    override val activeId: Flow<String> = activeIdState
    override val custom: Flow<List<Style>> = customState
    override val active: Flow<Style> = combine(activeIdState, customState, ::resolveActive)

    override suspend fun select(id: String) {
        activeIdState.value = id
    }

    override suspend fun save(style: Style): Style {
        val saved = uniqueCustom(style, customState.value)
        customState.update { list -> list.filterNot { it.id == saved.id } + saved }
        return saved
    }

    override suspend fun delete(id: String) {
        customState.update { list -> list.filterNot { it.id == id } }
        if (activeIdState.value == id) activeIdState.value = Presets.default.id
    }
}
