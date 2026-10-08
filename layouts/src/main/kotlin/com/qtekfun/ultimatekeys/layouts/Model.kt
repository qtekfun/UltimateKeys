// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.layouts

/** Non-character keys. Behavior lives in the IME; layouts only place them. */
enum class KeyAction {
    SHIFT,
    DELETE,
    ENTER,
    SPACE,
    GLOBE,
    EMOJI,
    MIC,
    SWITCH_LETTERS,
    SWITCH_SYMBOLS,
    SWITCH_SYMBOLS_2
}

sealed interface LayoutKey {
    /** Relative width inside the row. */
    val width: Float
}

/**
 * A key that types text.
 *
 * @property label text drawn on the key (and typed, unless [output] differs).
 * @property alternatives characters offered on long press.
 * @property hint small secondary label, or null.
 */
data class CharKey(
    val label: String,
    val output: String = label,
    val alternatives: List<String> = emptyList(),
    val hint: String? = null,
    override val width: Float = 1f
) : LayoutKey

data class ActionKey(
    val action: KeyAction,
    val label: String? = null,
    override val width: Float = 1f
) : LayoutKey

data class KeyRow(val keys: List<LayoutKey>)

data class KeyboardLayout(val id: String, val locale: String?, val rows: List<KeyRow>)
