// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

/** The built-in styles. Original designs with our own names; they cannot be edited, only duplicated. */
object Presets {
    val Ultimate = Style(
        id = "ultimate",
        name = "Ultimate",
        description = "Soft rounded keys on a calm background."
    )

    val all: List<Style> = listOf(Ultimate)

    val default: Style = Ultimate

    fun byId(id: String): Style? = all.firstOrNull { it.id == id }

    fun isPreset(id: String): Boolean = byId(id) != null
}
