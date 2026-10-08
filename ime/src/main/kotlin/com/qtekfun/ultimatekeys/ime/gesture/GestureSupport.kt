// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.gesture

import com.qtekfun.ultimatekeys.dictionaries.DictionaryLocator
import com.qtekfun.ultimatekeys.dictionaries.WordListParser
import com.qtekfun.ultimatekeys.gesture.GestureKey
import com.qtekfun.ultimatekeys.gesture.GestureKeyboard
import com.qtekfun.ultimatekeys.gesture.GestureVocabulary
import com.qtekfun.ultimatekeys.ime.surface.KeyGeometry
import com.qtekfun.ultimatekeys.layouts.CharKey
import java.util.Locale

/** Adapters between the keyboard surface, the dictionaries and the pure `:gesture` module. */
object GestureSupport {
    /**
     * Words below this frequency (of 255) are not offered to gestures. Over the pinned lists it keeps
     * about two thirds of the words and all the ones people actually glide.
     */
    const val MIN_FREQUENCY = 30

    /**
     * Most words kept per language, the most frequent ones. A vocabulary costs about 40 bytes a word and every
     * enabled language is scanned by each gesture, so many languages need a bound.
     */
    const val MAX_WORDS_PER_LANGUAGE = 120_000

    /** The letter keys of [geometry] as the decoder sees them. */
    fun keyboard(geometry: KeyGeometry): GestureKeyboard = GestureKeyboard(
        geometry.keys.mapNotNull { placed ->
            val key = placed.key as? CharKey ?: return@mapNotNull null
            val letter =
                key.label.singleOrNull()?.takeIf { it.isLetter() } ?: return@mapNotNull null
            GestureKey(letter, placed.left, placed.top, placed.right, placed.bottom)
        }
    )

    /**
     * Reads the installed word lists of [locales] (the first is the primary language; languages are named by
     * their BCP-47 tag) into one vocabulary, streaming each list. Takes a second or two per language: call it
     * off the main thread.
     */
    fun vocabulary(
        locator: DictionaryLocator,
        locales: List<Locale>,
        minFrequency: Int = MIN_FREQUENCY,
        maxWords: Int = MAX_WORDS_PER_LANGUAGE
    ): GestureVocabulary {
        val builder = GestureVocabulary.Builder(locales.map { it.toLanguageTag() }, minFrequency)
        locales.forEachIndexed { index, locale ->
            val file = locator.fileFor(locale) ?: return@forEachIndexed
            file.inputStream().use { input ->
                WordListParser.streamGzip(input) { entries ->
                    entries.filter {
                        !it.notAWord && !it.possiblyOffensive && it.frequency >= minFrequency
                    }.take(maxWords).forEach { builder.add(index, it.word, it.frequency) }
                }
            }
        }
        return builder.build()
    }
}
