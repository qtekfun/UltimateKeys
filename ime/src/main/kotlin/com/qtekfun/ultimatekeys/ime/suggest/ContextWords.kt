// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.suggest

/** Extracts the words before the cursor that matter for prediction. */
object ContextWords {
    private const val MAX_WORDS = 4
    private val sentenceEnd = Regex("[.!?¿¡\n]")
    private val word = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?")

    /** The last words of the current sentence, oldest first. */
    fun from(textBefore: String): List<String> {
        val sentence = textBefore.substring(
            sentenceEnd.findAll(textBefore).lastOrNull()?.range?.last?.plus(1) ?: 0
        )
        return word.findAll(sentence).map { it.value }.toList().takeLast(MAX_WORDS)
    }
}
