// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

import java.util.Locale

/**
 * Starts with [NoopSuggestionEngine] and switches to the real engine once its dictionaries are
 * ready (built in the background on first run), so the keyboard never waits for them.
 */
class SwappableSuggestionEngine(initial: SuggestionEngine = NoopSuggestionEngine) :
    SuggestionEngine {
    @Volatile
    private var current: SuggestionEngine = initial

    /** Replaces the engine; the previous one is closed. */
    fun swap(next: SuggestionEngine) {
        val old = current
        current = next
        if (old !== next) old.close()
    }

    override fun suggest(context: List<String>, composing: String, locale: Locale) =
        current.suggest(context, composing, locale)

    override fun predictNext(context: List<String>, locale: Locale) =
        current.predictNext(context, locale)

    override fun isValidWord(word: String, locale: Locale) = current.isValidWord(word, locale)

    override fun learn(word: String, context: List<String>, locale: Locale) =
        current.learn(word, context, locale)

    override fun addToUserDictionary(word: String, locale: Locale) =
        current.addToUserDictionary(word, locale)

    override fun removeFromUserDictionary(word: String, locale: Locale) =
        current.removeFromUserDictionary(word, locale)

    override fun userDictionaryWords(locale: Locale) = current.userDictionaryWords(locale)

    override fun clearLearned() = current.clearLearned()

    override fun close() = current.close()
}
