// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine.mixed

import com.qtekfun.ultimatekeys.engine.Suggestion
import com.qtekfun.ultimatekeys.engine.SuggestionEngine
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Mixing three to six languages: weights, merging, autocorrect, learning and sharing of engines. */
class ManyLanguagesTest {
    private class Fake(
        val vocabulary: Set<String>,
        val suggestions: List<Suggestion> = emptyList(),
        val predictions: List<Suggestion> = emptyList()
    ) : SuggestionEngine {
        val learned = mutableListOf<String>()
        var closed = 0

        override fun suggest(context: List<String>, composing: String, locale: Locale) = suggestions

        override fun predictNext(context: List<String>, locale: Locale) = predictions

        override fun isValidWord(word: String, locale: Locale) = word in vocabulary

        override fun learn(word: String, context: List<String>, locale: Locale) {
            learned += word
        }

        override fun addToUserDictionary(word: String, locale: Locale) = Unit

        override fun removeFromUserDictionary(word: String, locale: Locale) = Unit

        override fun userDictionaryWords(locale: Locale) = emptyList<String>()

        override fun clearLearned() = Unit

        override fun close() {
            closed++
        }
    }

    private fun tag(language: String) = Locale.forLanguageTag(language)

    private val es = tag("es")
    private val en = tag("en")
    private val fr = tag("fr")
    private val de = tag("de")

    private val vocabularies = mapOf(
        "es" to setOf("hola", "casa", "no"),
        "en" to setOf("hello", "house", "no"),
        "fr" to setOf("bonjour", "maison", "avec", "et"),
        "de" to setOf("hallo", "haus", "und", "et"),
        "ru" to setOf("привет", "дом"),
        "tr" to setOf("merhaba", "ev")
    )

    private fun engines(
        languages: List<String>,
        suggestions: Map<String, List<Suggestion>> = emptyMap()
    ): Map<Locale, Fake> = languages.associate {
        tag(it) to Fake(vocabularies.getValue(it), suggestions[it].orEmpty())
    }

    private fun mixed(
        languages: List<String>,
        primary: Locale = es,
        suggestions: Map<String, List<Suggestion>> = emptyMap(),
        closeEngines: Boolean = true
    ): MixedSuggestionEngine = MixedSuggestionEngine(
        engines(languages, suggestions),
        primary,
        closeEngines = closeEngines
    )

    private fun s(word: String, score: Int, auto: Boolean = false) = Suggestion(word, score, auto)

    @Test
    fun `priors keep a floor for every secondary language and sum to one`() {
        val expectedPrimary = mapOf(2 to 0.75, 3 to 0.75, 4 to 0.7, 5 to 0.6, 6 to 0.5)
        val all = listOf("es", "en", "fr", "de", "ru", "tr")
        for ((count, primary) in expectedPrimary) {
            val weights = mixed(all.take(count)).languageWeights(emptyList())
            assertEquals(count, weights.size)
            assertEquals(primary, weights.getValue("es"), 1e-9, "primary with $count")
            assertEquals(1.0, weights.values.sum(), 1e-9)
            weights.filterKeys { it != "es" }.values.forEach {
                assertTrue(it >= 0.1 - 1e-9 || count <= 3, "secondary $it with $count")
            }
        }
    }

    @Test
    fun `the primary language is the active one`() {
        val weights = mixed(
            listOf("es", "en", "fr", "de"),
            primary = fr
        ).languageWeights(emptyList())
        assertTrue(weights.getValue("fr") > weights.getValue("es"))
        assertEquals(0.7, weights.getValue("fr"), 1e-9)
    }

    @Test
    fun `context in a third language moves the weight to it`() {
        val weights = mixed(listOf("es", "en", "fr", "de"))
            .languageWeights(listOf("bonjour", "maison"))
        assertTrue(weights.getValue("fr") > weights.getValue("es"))
        assertEquals(1.0, weights.values.sum(), 1e-9)
        assertTrue(weights.getValue("de") < weights.getValue("fr"))
    }

    @Test
    fun `a word shared by two of four languages gives each of them half the evidence`() {
        val weights = mixed(listOf("es", "en", "fr", "de")).languageWeights(listOf("et"))
        assertEquals(weights.getValue("fr"), weights.getValue("de"), 1e-9)
        assertTrue(weights.getValue("fr") > weights.getValue("en"))
    }

    @Test
    fun `words valid in every language or none are no evidence`() {
        val same = mixed(listOf("es", "en", "fr")).languageWeights(emptyList())
        assertEquals(same, mixed(listOf("es", "en", "fr")).languageWeights(listOf("zzzz")))
    }

    @Test
    fun `suggestions of all languages are merged and the best weighted wins`() {
        val result = mixed(
            listOf("es", "en", "fr"),
            suggestions = mapOf(
                "es" to listOf(s("hola", 100)),
                "en" to listOf(s("hello", 100)),
                "fr" to listOf(s("bonjour", 100))
            )
        ).suggest(listOf("avec", "maison"), "bonj", es)
        assertEquals("bonjour", result.first().word)
        assertEquals(setOf("hola", "hello", "bonjour"), result.map { it.word }.toSet())
        assertEquals(fr, result.first().locale)
    }

    @Test
    fun `a word in another script is found whatever the primary language is`() {
        val result = mixed(
            listOf("es", "ru", "tr"),
            suggestions = mapOf("ru" to listOf(s("привет", 100)), "tr" to listOf(s("merhaba", 50)))
        ).suggest(listOf("привет", "дом"), "прив", es)
        assertEquals("привет", result.first().word)
    }

    @Test
    fun `prediction merges every language and never autocorrects`() {
        val engines = mapOf(
            es to Fake(vocabularies.getValue("es"), predictions = listOf(s("casa", 10, true))),
            fr to Fake(vocabularies.getValue("fr"), predictions = listOf(s("maison", 10, true))),
            de to Fake(vocabularies.getValue("de"), predictions = listOf(s("haus", 10, true)))
        )
        val result = MixedSuggestionEngine(engines, es).predictNext(emptyList(), es)
        assertEquals(setOf("casa", "maison", "haus"), result.map { it.word }.toSet())
        assertTrue(result.none { it.autoCorrect })
        assertEquals("casa", result.first().word)
    }

    @Test
    fun `autocorrect is allowed for a clearly leading primary even among six languages`() {
        val result = mixed(
            listOf("es", "en", "fr", "de", "ru", "tr"),
            suggestions = mapOf("es" to listOf(s("hola", 100, auto = true)))
        ).suggest(emptyList(), "hoal", es)
        assertEquals("hola", result.first().word)
        assertTrue(result.first().autoCorrect)
    }

    @Test
    fun `autocorrect is withheld when two languages are close`() {
        val result = mixed(
            listOf("es", "en", "fr"),
            suggestions = mapOf("fr" to listOf(s("bonjour", 100, auto = true)))
        ).suggest(listOf("hola", "hello"), "bonjuor", es)
        assertFalse(result.first().autoCorrect)
    }

    @Test
    fun `autocorrect for a secondary language needs context pointing to it`() {
        val suggestions = mapOf("fr" to listOf(s("bonjour", 100, auto = true)))
        val engine = mixed(listOf("es", "en", "fr", "de"), suggestions = suggestions)
        assertFalse(engine.suggest(emptyList(), "bonjuor", es).first().autoCorrect)
        assertTrue(
            engine.suggest(listOf("avec", "maison", "bonjour"), "bonjuor", es).first().autoCorrect
        )
    }

    @Test
    fun `a word is valid when any of the languages accepts it`() {
        val engine = mixed(listOf("es", "en", "fr", "de", "ru"))
        assertTrue(engine.isValidWord("привет", es))
        assertTrue(engine.isValidWord("maison", es))
        assertFalse(engine.isValidWord("qzxwv", es))
    }

    @Test
    fun `learning goes to the language the word was detected as`() {
        val engines = engines(listOf("es", "en", "fr", "de"))
        val mixed = MixedSuggestionEngine(engines, es)
        mixed.learn("maison", emptyList(), es)
        assertEquals(listOf("maison"), engines.getValue(fr).learned)
        assertTrue(engines.filterKeys { it != fr }.values.all { it.learned.isEmpty() })

        // Unknown everywhere: the context decides.
        mixed.learn("zzzz", listOf("haus", "und", "hallo"), es)
        assertEquals(listOf("zzzz"), engines.getValue(de).learned)

        // Known in two languages: the context decides between those too, falling back to the primary.
        mixed.learn("et", emptyList(), es)
        assertEquals(listOf("zzzz"), engines.getValue(de).learned.filter { it == "zzzz" })
        assertTrue(engines.getValue(es).learned.contains("et"))
    }

    @Test
    fun `a shared engine is not closed by the mixer so it can be swapped`() {
        val shared = Fake(vocabularies.getValue("es"))
        val first =
            MixedSuggestionEngine(mapOf(es to shared, fr to shared), es, closeEngines = false)
        val second =
            MixedSuggestionEngine(mapOf(es to shared, de to shared), es, closeEngines = false)
        first.close()
        assertEquals(0, shared.closed)
        second.close()
        assertEquals(0, shared.closed)

        val owner = MixedSuggestionEngine(mapOf(es to shared), es)
        owner.close()
        assertEquals(1, shared.closed)
    }

    @Test
    fun `config validation covers the new floor`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            MixedConfig(minSecondaryPrior = 1.5)
        }
        // A low primary prior is respected: the floor never takes weight from the secondary languages.
        val weights = MixedSuggestionEngine(
            mapOf(es to Fake(emptySet()), en to Fake(emptySet()), fr to Fake(emptySet())),
            es,
            MixedConfig(primaryPrior = 0.2)
        ).languageWeights(emptyList())
        assertEquals(0.2, weights.getValue("es"), 1e-9)
        assertEquals(0.4, weights.getValue("en"), 1e-9)
    }
}
