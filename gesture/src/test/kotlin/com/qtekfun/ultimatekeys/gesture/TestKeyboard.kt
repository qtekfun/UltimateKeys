// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

/** A plain QWERTY of 100 x 120 pixel keys, rows offset like a real phone keyboard. */
object TestKeyboard {
    const val KEY_WIDTH = 100f
    const val KEY_HEIGHT = 120f

    private val rows = listOf("qwertyuiop" to 0f, "asdfghjkl" to 50f, "zxcvbnm" to 150f)

    fun keys(): List<GestureKey> = rows.flatMapIndexed { row, (letters, offset) ->
        letters.mapIndexed { i, c ->
            val left = offset + i * KEY_WIDTH
            GestureKey(c, left, row * KEY_HEIGHT, left + KEY_WIDTH, (row + 1) * KEY_HEIGHT)
        }
    }

    fun keyboard() = GestureKeyboard(keys())

    fun center(letter: Char): Pair<Float, Float> =
        keys().first { it.letter == letter }.let { it.centerX to it.centerY }

    /** The exact path through the centres of [word]'s keys, one point per key. */
    fun pathOf(word: String): FloatArray {
        val out = ArrayList<Float>()
        word.forEach {
            val (x, y) = center(it)
            out += x
            out += y
        }
        return out.toFloatArray()
    }

    fun vocabulary(
        vararg entries: Triple<Int, String, Int>,
        languages: List<String> = listOf("es", "en")
    ) = GestureVocabulary.Builder(languages).also { b ->
        entries.forEach { (language, word, frequency) -> b.add(language, word, frequency) }
    }.build()
}
