// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

import java.util.Locale

/** A suggestion for the word being typed or the next word. */
data class Suggestion(
    val word: String,
    /** Higher is better. Comparable only within one result list. */
    val score: Int,
    /** True when it is safe to apply automatically (autocorrect) on space or punctuation. */
    val autoCorrect: Boolean = false,
    val locale: Locale? = null,
)

/**
 * Word suggestions, autocorrect and learning. Implementations may be native (AOSP engine) or fakes.
 * Not main-thread safe unless an implementation says so: call from a single background dispatcher.
 */
interface SuggestionEngine {
    /** Candidates for [composing], given the words typed before it ([context], oldest first). */
    fun suggest(context: List<String>, composing: String, locale: Locale): List<Suggestion>

    /** Likely next words after [context]. */
    fun predictNext(context: List<String>, locale: Locale): List<Suggestion>

    fun isValidWord(word: String, locale: Locale): Boolean

    /** Learns that [word] was typed after [context]. Must be a no-op while private mode is on. */
    fun learn(word: String, context: List<String>, locale: Locale)

    fun addToUserDictionary(word: String, locale: Locale)

    fun removeFromUserDictionary(word: String, locale: Locale)

    fun userDictionaryWords(locale: Locale): List<String>

    /** Forgets learned history and user words. */
    fun clearLearned()

    fun close()
}
