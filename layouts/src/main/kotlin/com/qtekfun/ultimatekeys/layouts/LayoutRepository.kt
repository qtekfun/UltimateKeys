// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.layouts

/** The pages a keyboard can show for one language. */
data class KeyboardPages(
    val letters: KeyboardLayout,
    val symbols1: KeyboardLayout,
    val symbols2: KeyboardLayout
)

/** Loads the bundled layouts (JSON resources inside this module). */
object LayoutRepository {
    private val cache = HashMap<String, KeyboardLayout>()

    @Synchronized
    fun load(id: String): KeyboardLayout = cache.getOrPut(id) {
        val stream = LayoutRepository::class.java.getResourceAsStream("/uk/layouts/$id.json")
            ?: throw LayoutParseException("Unknown layout $id")
        LayoutParser.parse(stream.bufferedReader().use { it.readText() })
    }

    val letterLayoutIds: List<String> = listOf("es_qwerty", "en_qwerty")

    fun pages(letterLayoutId: String, numberRow: Boolean): KeyboardPages = KeyboardPages(
        letters = withNumberRow(load(letterLayoutId), numberRow),
        symbols1 = load("symbols_1"),
        symbols2 = load("symbols_2")
    )

    /** Prepends the number row and drops the digit hints it makes redundant. */
    fun withNumberRow(layout: KeyboardLayout, enabled: Boolean): KeyboardLayout {
        if (!enabled) return layout
        val stripped = layout.rows.map { row ->
            KeyRow(
                row.keys.map { key ->
                    if (key is CharKey && key.hint != null) {
                        key.copy(hint = null, alternatives = key.alternatives - key.hint)
                    } else {
                        key
                    }
                }
            )
        }
        return layout.copy(rows = listOf(load("number_row").rows.first()) + stripped)
    }
}
