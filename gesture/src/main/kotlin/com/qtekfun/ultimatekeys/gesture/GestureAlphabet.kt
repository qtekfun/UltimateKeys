// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

import java.text.Normalizer

/**
 * The letter keys a gesture can pass over, and how a dictionary word maps onto them.
 *
 * The alphabet holds every letter that has a key of its own on some bundled layout: the Latin letters, the extra
 * letters of the Nordic, Turkish, Baltic and South Slavic layouts, Cyrillic and Greek. A word is traced by its keys:
 * a letter with a key of its own is traced by that key; any other accented letter is traced by its base letter
 * (`está` is traced as `esta`); apostrophes are skipped (`don't` is traced as `dont`); repeated letters collapse
 * (a finger cannot dwell on a key to type `ll`).
 *
 * A letter whose own key is missing from the layout on screen falls back to its base letter's key (see
 * [fallbackIndex]), so a German word with `ö` can still be traced on a layout that has no `ö` key.
 */
object GestureAlphabet {
    private const val LATIN = "abcdefghijklmnopqrstuvwxyzñç"
    private const val NORDIC_TURKISH_BALTIC = "åæøüöäšđčćžığşłßœ"
    private const val CYRILLIC = "йцукенгшщзхъфывапролджэячсмитьбюљњјћѕџђ"
    private const val GREEK = "ςερτυθιοπασδφγηξκλζχψωβνμ"

    /** The key identities, in index order (the first 28 are the historic Latin ones). */
    const val LETTERS = LATIN + NORDIC_TURKISH_BALTIC + CYRILLIC + GREEK

    /** Number of distinct keys a template can use. */
    val size: Int = LETTERS.length

    private const val TABLE_SIZE = 0x500
    private const val NOT_A_KEY = -1
    private const val SKIPPED = -2
    private const val RIGHT_QUOTE = '’'

    /** Letters that do not decompose into a base letter, and the letter their key stands in for. */
    private val manualBase = mapOf(
        'æ' to 'a',
        'ø' to 'o',
        'đ' to 'd',
        'ı' to 'i',
        'ł' to 'l',
        'ß' to 's',
        'œ' to 'o',
        'ς' to 'σ',
        'љ' to 'л',
        'њ' to 'н'
    )

    private val table = IntArray(TABLE_SIZE) { code -> classify(code.toChar()) }

    private fun baseOf(lower: Char): Char? {
        manualBase[lower]?.let { return it }
        val base = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD)[0]
        return base.takeIf { it != lower }
    }

    private fun classify(c: Char): Int {
        val lower = c.lowercaseChar()
        val direct = LETTERS.indexOf(lower)
        return when {
            direct >= 0 -> direct
            lower == '\'' || lower == '’' -> SKIPPED
            !lower.isLetter() -> NOT_A_KEY
            else -> baseOf(lower)?.let { LETTERS.indexOf(it) } ?: NOT_A_KEY
        }
    }

    /** The key index for a letter printed on a layout key, or -1 when it is not a gesture key. */
    fun keyIndex(letter: Char): Int {
        val lower = letter.lowercaseChar()
        return LETTERS.indexOf(lower)
    }

    /**
     * The index of the letter whose key stands in for [index] when a layout has no key of its own for it (`ö` is
     * traced by `o`), or -1 when there is none.
     */
    fun fallbackIndex(index: Int): Int =
        baseOf(LETTERS[index])?.let { LETTERS.indexOf(it) } ?: NOT_A_KEY

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
