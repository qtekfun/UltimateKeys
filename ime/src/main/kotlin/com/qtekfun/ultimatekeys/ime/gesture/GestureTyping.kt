// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.gesture

import com.qtekfun.ultimatekeys.engine.SuggestionEngine
import com.qtekfun.ultimatekeys.gesture.GestureContext
import com.qtekfun.ultimatekeys.gesture.GestureDecoder
import com.qtekfun.ultimatekeys.gesture.GestureKeyboard
import com.qtekfun.ultimatekeys.gesture.GestureVocabulary
import com.qtekfun.ultimatekeys.ime.suggest.ContextWords
import java.util.Locale

/**
 * Gesture typing for the keyboard: owns the decoder, which exists once the word lists are loaded, and
 * feeds it what the suggestion engine knows (the words just written, the words it expects next, the
 * person's own words). Gestures are ignored until [install] is called.
 *
 * [decode] calls the engine, so run it on the same single background dispatcher as the suggestions.
 */
class GestureTyping(private val engine: SuggestionEngine) {
    @Volatile
    private var decoder: GestureDecoder? = null

    @Volatile
    private var vocabulary: GestureVocabulary? = null

    val ready: Boolean get() = decoder != null

    /** The languages of the installed vocabulary (BCP-47 tags), empty before [install]. */
    val languages: List<String> get() = vocabulary?.languages.orEmpty()

    /**
     * Starts accepting gestures with [vocabulary], replacing any earlier one (the enabled languages changed).
     * [primary] is the tag of the language being written, the first of the vocabulary when null or unknown.
     */
    fun install(vocabulary: GestureVocabulary, primary: String? = null) {
        this.vocabulary = vocabulary
        setPrimary(primary)
    }

    /** Tells the decoder which language is the active one; cheap, call it when the person switches language. */
    fun setPrimary(primary: String?) {
        val installed = vocabulary ?: return
        val index = installed.languages.indexOf(primary).coerceAtLeast(0)
        decoder = GestureDecoder(installed, primaryLanguage = index)
    }

    /** The words [path] most likely means, best first; empty when there is nothing to say. */
    fun decode(
        path: FloatArray,
        keyboard: GestureKeyboard,
        contextText: String,
        locale: Locale
    ): List<String> {
        val active = decoder ?: return emptyList()
        val previous = ContextWords.from(contextText)
        val context = GestureContext(
            previousWords = previous,
            nextWords = nextWords(previous, locale),
            userWords = engine.userDictionaryWords(locale)
        )
        return active.decode(path, keyboard, context).map { it.word }
    }

    private fun nextWords(previous: List<String>, locale: Locale): Map<String, Float> {
        val predicted = engine.predictNext(previous, locale)
        val best = predicted.maxOfOrNull { it.score }?.takeIf { it > 0 } ?: return emptyMap()
        return predicted.take(MAX_PREDICTIONS).associate {
            it.word.lowercase() to
                it.score.toFloat() / best
        }
    }

    private companion object {
        const val MAX_PREDICTIONS = 10
    }
}
