// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

/**
 * How the language settings are stored in the preferences: a list as space-separated tags and a map as
 * `tag=layout` pairs separated by spaces. Neither tags nor layout ids contain spaces or `=`. Decoding is lenient
 * (the settings are sanitised against the catalog afterwards); an empty string is an empty list or map.
 */
internal object LanguageSettingsCodec {
    fun encodeList(values: List<String>): String = values.joinToString(" ")

    fun decodeList(text: String): List<String> = text.split(' ').filter { it.isNotBlank() }

    fun encodeMap(values: Map<String, String>): String =
        values.entries.joinToString(" ") { "${it.key}=${it.value}" }

    fun decodeMap(text: String): Map<String, String> = decodeList(text).mapNotNull {
        val at = it.indexOf('=')
        if (at > 0 && at < it.lastIndex) it.substring(0, at) to it.substring(at + 1) else null
    }.toMap()
}
