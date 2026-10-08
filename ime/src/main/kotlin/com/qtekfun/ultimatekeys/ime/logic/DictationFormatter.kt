// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.logic

import java.util.Locale

/**
 * Fits dictated text into what is already in the field: a space where one is needed, a capital
 * letter where a sentence starts and a small one where it continues. Pure, so it is testable.
 */
object DictationFormatter {
    /**
     * @param raw what the model heard
     * @param before the text before the cursor (as much as is known)
     * @param after the text after the cursor
     * @param language the language code the speech was decoded as ("es", "en", or empty)
     * @param prose false for fields where prose rules do not apply (email, URL, numbers): the text is
     *   then inserted as heard, without spacing or case changes
     */
    fun format(
        raw: String,
        before: CharSequence,
        after: CharSequence,
        language: String,
        prose: Boolean
    ): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        // Addresses, URLs and numbers: the words go in exactly as heard.
        if (!prose) return text
        val adjusted = adjustCase(text, before, language)
        val lead = if (needsSpaceBefore(before, adjusted)) " " else ""
        val tail = if (needsSpaceAfter(after, adjusted)) " " else ""
        return lead + adjusted + tail
    }

    private fun adjustCase(text: String, before: CharSequence, language: String): String {
        val index = text.indexOfFirst { it.isLetter() }
        if (index < 0) return text
        val firstWord = text.substring(index).takeWhile { it.isLetter() || it == '\'' }
        val replacement = when {
            startsSentence(before) -> firstWord.replaceFirstChar { it.titlecase(Locale.ROOT) }

            firstWord.drop(1).any { it.isUpperCase() } -> firstWord

            firstWord.lowercase(Locale.ROOT) in functionWords(language) ->
                firstWord.replaceFirstChar { it.lowercase(Locale.ROOT) }

            else -> firstWord
        }
        return text.substring(0, index) + replacement + text.substring(index + firstWord.length)
    }

    /** True at the start of the field, after a line break and after a sentence's end. */
    private fun startsSentence(before: CharSequence): Boolean {
        val trimmed = before.trimEnd(' ', '\t', ' ')
        val last = trimmed.lastOrNull() ?: return true
        return last == '\n' || last in SENTENCE_END
    }

    private fun needsSpaceBefore(before: CharSequence, text: String): Boolean {
        val last = before.lastOrNull() ?: return false
        if (last.isWhitespace() || last in OPENERS) return false
        return text.first() !in CLOSERS_AND_PUNCTUATION
    }

    private fun needsSpaceAfter(after: CharSequence, text: String): Boolean {
        val next = after.firstOrNull() ?: return false
        return !next.isWhitespace() && next !in CLOSERS_AND_PUNCTUATION && text.last() !in OPENERS
    }

    private fun functionWords(language: String): Set<String> = when (language) {
        "es" -> SPANISH_FUNCTION_WORDS
        "en" -> ENGLISH_FUNCTION_WORDS
        else -> SPANISH_FUNCTION_WORDS + ENGLISH_FUNCTION_WORDS
    }

    private const val SENTENCE_END = ".!?…"
    private const val OPENERS = "([{\"'¿¡«“‘"
    private const val CLOSERS_AND_PUNCTUATION = ".,;:!?…)]}\"'»”’%"

    // Words the model capitalizes only because they start its output; names and "I" are absent.
    private val ENGLISH_FUNCTION_WORDS = setOf(
        "the", "a", "an", "and", "but", "or", "so", "to", "of", "in", "on", "at", "for", "with",
        "is", "are", "was", "were", "it", "this", "that", "then", "also", "because", "if", "when",
        "we", "you", "they", "he", "she", "my", "your", "not", "do", "does"
    )
    private val SPANISH_FUNCTION_WORDS = setOf(
        "el", "la", "los", "las", "un", "una", "y", "o", "pero", "que", "de", "en", "a", "con",
        "por", "para", "es", "no", "si", "se", "lo", "me", "te", "mi", "tu", "su", "como",
        "cuando", "porque", "entonces", "también", "ya", "al", "del", "nos", "le", "muy"
    )
}
