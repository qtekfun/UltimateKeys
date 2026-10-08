// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

import java.text.Normalizer

/**
 * The letter keys a gesture can pass over, and how a dictionary word maps onto them.
 *
 * A word is traced by its base letters: accents are dropped (`está` is traced as `esta`), apostrophes
 * are skipped (`don't` is traced as `dont`) and repeated letters collapse (a finger cannot dwell on a key
 * to type `ll`). `ñ` and `ç` are letters of their own because the Spanish and Catalan style layouts carry
 * keys for them.
 */
object GestureAlphabet {
    /** The key identities, in index order. */
    const val LETTERS = "abcdefghijklmnopqrstuvwxyzñç"

    /** Number of distinct keys a template can use. */
    val size: Int = LETTERS.length

    private const val TABLE_SIZE = 0x250
    private const val NOT_A_KEY = -1
    private const val SKIPPED = -2
    private const val RIGHT_QUOTE = '\u2019'

    private val table = IntArray(TABLE_SIZE) { code -> classify(code.toChar()) }

    private fun classify(c: Char): Int {
        val lower = c.lowercaseChar()
        val direct = LETTERS.indexOf(lower)
        return when {
            direct >= 0 -> direct

            lower == '\'' || lower == '’' -> SKIPPED

            !lower.isLetter() -> NOT_A_KEY

            else -> {
                val base = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD)[0]
                val index = LETTERS.indexOf(base)
                if (index in 0 until LETTERS.length - 2) index else NOT_A_KEY
            }
        }
    }

    /** The key index for a letter printed on a layout key, or -1 when it is not a gesture key. */
    fun keyIndex(letter: Char): Int {
        val lower = letter.lowercaseChar()
        return LETTERS.indexOf(lower)
    }

    /**
     * The keys [word] passes over, one index per key, with consecutive repeats collapsed. Null when the
     * word cannot be traced (digits, symbols, letters without a key) or needs fewer than two keys.
     */
    fun sequenceOf(word: String): ByteArray? {
        val out = ByteArray(word.length)
        var count = 0
        for (c in word) {
            val code = codeOf(c)
            when {
                code == NOT_A_KEY -> return null
                code == SKIPPED || (count > 0 && out[count - 1].toInt() == code) -> Unit
                else -> out[count++] = code.toByte()
            }
        }
        return if (count < MIN_KEYS) null else out.copyOf(count)
    }

    private fun codeOf(c: Char): Int = when {
        c.code < TABLE_SIZE -> table[c.code]
        c == RIGHT_QUOTE -> SKIPPED
        else -> NOT_A_KEY
    }

    private const val MIN_KEYS = 2
}
