// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

import java.util.Locale

/** An engine with no knowledge: used until a real one is loaded, and when suggestions are off. */
object NoopSuggestionEngine : SuggestionEngine {
    override fun suggest(context: List<String>, composing: String, locale: Locale) =
        emptyList<Suggestion>()

    override fun predictNext(context: List<String>, locale: Locale) = emptyList<Suggestion>()

    override fun isValidWord(word: String, locale: Locale) = true

    override fun learn(word: String, context: List<String>, locale: Locale) = Unit

    override fun addToUserDictionary(word: String, locale: Locale) = Unit

    override fun removeFromUserDictionary(word: String, locale: Locale) = Unit

    override fun userDictionaryWords(locale: Locale) = emptyList<String>()

    override fun clearLearned() = Unit

    override fun close() = Unit
}
