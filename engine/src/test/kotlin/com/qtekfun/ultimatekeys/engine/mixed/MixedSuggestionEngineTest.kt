// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine.mixed

import com.qtekfun.ultimatekeys.engine.Suggestion
import com.qtekfun.ultimatekeys.engine.SuggestionEngine
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Scripted engine: fixed vocabulary, fixed candidate lists, records every mutation. */
private class FakeEngine(
    val vocabulary: Set<String> = emptySet(),
    val suggestions: List<Suggestion> = emptyList(),
    val predictions: List<Suggestion> = emptyList(),
    val closeFailure: RuntimeException? = null
) : SuggestionEngine {
    val learned = mutableListOf<Triple<String, List<String>, Locale>>()
    val added = mutableListOf<String>()
    val removed = mutableListOf<String>()
    var userWords: List<String> = emptyList()
    var cleared = 0
    var closed = 0
    var suggestCalls = mutableListOf<Locale>()

    override fun suggest(
        context: List<String>,
        composing: String,
        locale: Locale
    ): List<Suggestion> {
        suggestCalls += locale
        return suggestions
    }

    override fun predictNext(context: List<String>, locale: Locale) = predictions

    override fun isValidWord(word: String, locale: Locale) = word in vocabulary

    override fun learn(word: String, context: List<String>, locale: Locale) {
        learned += Triple(word, context, locale)
    }

    override fun addToUserDictionary(word: String, locale: Locale) {
        added += word
    }

    override fun removeFromUserDictionary(word: String, locale: Locale) {
        removed += word
    }

    override fun userDictionaryWords(locale: Locale) = userWords

    override fun clearLearned() {
        cleared++
    }

    override fun close() {
        closed++
        closeFailure?.let { throw it }
    }
}

class MixedSuggestionEngineTest {
    private val es = Locale.forLanguageTag("es")
    private val en = Locale.forLanguageTag("en")

    private fun s(word: String, score: Int, auto: Boolean = false, locale: Locale? = null) =
        Suggestion(word, score, auto, locale)

    private fun engine(
        spanish: FakeEngine,
        english: FakeEngine,
        config: MixedConfig = MixedConfig()
    ) = MixedSuggestionEngine(mapOf(es to spanish, en to english), es, config)

    private val spanishVocabulary = setOf("hola", "casa", "el", "perro", "no")
    private val englishVocabulary = setOf("hello", "house", "the", "dog", "no")

    @Test
    fun `empty context yields exactly the prior with Spanish primary`() {
        val weights = engine(
            FakeEngine(spanishVocabulary),
            FakeEngine(englishVocabulary)
        ).languageWeights(emptyList())
        assertEquals(0.75, weights.getValue("es"), 1e-9)
        assertEquals(0.25, weights.getValue("en"), 1e-9)
    }

    @Test
    fun `English context shifts weights towards English and they always sum to one`() {
        val mixed = engine(FakeEngine(spanishVocabulary), FakeEngine(englishVocabulary))
        val one = mixed.languageWeights(listOf("hello"))
        val two = mixed.languageWeights(listOf("hello", "the"))
        assertEquals(1.0, one.values.sum(), 1e-9)
        assertEquals(1.0, two.values.sum(), 1e-9)
        assertEquals((0.25 + 1.0) / 2.0, one.getValue("en"), 1e-9)
        assertTrue(two.getValue("en") > one.getValue("en"))
    }

    @Test
    fun `words valid in both or neither language are not evidence`() {
        val mixed = engine(FakeEngine(spanishVocabulary), FakeEngine(englishVocabulary))
        assertEquals(0.75, mixed.languageWeights(listOf("no", "zzzz")).getValue("es"), 1e-9)
    }

    @Test
    fun `recent words count more than old ones and only the last N are used`() {
        val mixed =
            engine(
                FakeEngine(spanishVocabulary),
                FakeEngine(englishVocabulary),
                MixedConfig(contextWords = 2)
            )
        val recentSpanish = mixed.languageWeights(listOf("hello", "hello", "hola", "hola"))
        // the two English words fall outside the window of 2
        assertTrue(recentSpanish.getValue("es") > 0.75)
        val recentEnglish = mixed.languageWeights(listOf("hola", "the"))
        val recentSpanish2 = mixed.languageWeights(listOf("the", "hola"))
        assertTrue(recentEnglish.getValue("en") > recentSpanish2.getValue("en"))
    }

    @Test
    fun `blank context words are ignored`() {
        val mixed = engine(FakeEngine(spanishVocabulary), FakeEngine(englishVocabulary))
        assertEquals(
            mixed.languageWeights(listOf("hello")),
            mixed.languageWeights(listOf("", "hello", " "))
        )
    }

    @Test
    fun `merge weights each list by its language confidence`() {
        val spanish = FakeEngine(spanishVocabulary, listOf(s("casa", 200), s("caso", 100)))
        val english = FakeEngine(englishVocabulary, listOf(s("case", 50), s("cash", 25)))
        val result = engine(spanish, english).suggest(emptyList(), "cas", es)
        assertEquals(listOf("casa", "caso", "case", "cash"), result.map { it.word })
        assertEquals(listOf(750, 375, 250, 125), result.map { it.score })
        assertEquals(listOf(es, es, en, en), result.map { it.locale })
    }

    @Test
    fun `English context promotes English candidates above Spanish ones`() {
        val spanish = FakeEngine(spanishVocabulary, listOf(s("casa", 200)))
        val english = FakeEngine(englishVocabulary, listOf(s("case", 50)))
        val mixed = engine(spanish, english)
        assertEquals("casa", mixed.suggest(emptyList(), "cas", es).first().word)
        assertEquals("case", mixed.suggest(listOf("the", "hello", "dog"), "cas", es).first().word)
    }

    @Test
    fun `identical words are deduplicated case-insensitively and their scores add up`() {
        val spanish = FakeEngine(spanishVocabulary, listOf(s("No", 100), s("nos", 90)))
        val english = FakeEngine(englishVocabulary, listOf(s("no", 100)))
        val result = engine(spanish, english).suggest(emptyList(), "no", es)
        assertEquals(listOf("No", "nos"), result.map { it.word })
        assertEquals(1000, result[0].score) // 0.75 + 0.25
        assertEquals(es, result[0].locale) // strongest contributor keeps spelling and locale
        assertEquals(675, result[1].score)
    }

    @Test
    fun `ties go to the stronger language, then the primary, then alphabetical`() {
        val spanish = FakeEngine(emptySet(), listOf(s("zeta", 10), s("beta", 10)))
        val english = FakeEngine(emptySet(), listOf(s("alpha", 10)))
        // Spanish weight 0.75 beats English 0.25 on equal scores: both Spanish words first, alphabetical.
        assertEquals(
            listOf("beta", "zeta", "alpha"),
            engine(spanish, english).suggest(emptyList(), "x", es).map {
                it.word
            }
        )

        // With equal weights (primaryPrior 0.5) the primary language wins the tie.
        val even = MixedConfig(primaryPrior = 0.5)
        assertEquals(
            listOf("beta", "zeta", "alpha"),
            engine(spanish, english, even).suggest(emptyList(), "x", es).map {
                it.word
            }
        )

        // Same score, same weight, same language: alphabetical.
        val englishTie = FakeEngine(emptySet(), listOf(s("pear", 5), s("apple", 5)))
        val onlyEnglish = MixedSuggestionEngine(mapOf(en to englishTie), en)
        assertEquals(
            listOf("apple", "pear"),
            onlyEnglish.suggest(emptyList(), "x", en).map {
                it.word
            }
        )
    }

    @Test
    fun `a tied word from both languages is attributed to the primary`() {
        val spanish = FakeEngine(emptySet(), listOf(s("taxi", 10)))
        val english = FakeEngine(emptySet(), listOf(s("taxi", 10)))
        val result = engine(
            spanish,
            english,
            MixedConfig(primaryPrior = 0.5)
        ).suggest(emptyList(), "ta", es)
        assertEquals(es, result.single().locale)
    }

    @Test
    fun `engine supplied locale is preserved and result size is capped`() {
        val spanish =
            FakeEngine(
                emptySet(),
                (1..10).map {
                    s("w$it", 100 - it, locale = Locale.forLanguageTag("es-MX"))
                }
            )
        val english = FakeEngine()
        val result = engine(
            spanish,
            english,
            MixedConfig(maxResults = 3)
        ).suggest(emptyList(), "w", es)
        assertEquals(3, result.size)
        assertEquals(Locale.forLanguageTag("es-MX"), result.first().locale)
    }

    @Test
    fun `negative and zero scores do not break normalisation`() {
        val spanish = FakeEngine(emptySet(), listOf(s("a", 0), s("b", -5)))
        val result = engine(spanish, FakeEngine()).suggest(emptyList(), "x", es)
        assertEquals(listOf(0, 0), result.map { it.score })
    }

    @Test
    fun `each engine is queried with its own locale regardless of the hint`() {
        val spanish = FakeEngine()
        val english = FakeEngine()
        engine(spanish, english).suggest(emptyList(), "x", Locale.FRENCH)
        assertEquals(listOf(es), spanish.suggestCalls)
        assertEquals(listOf(en), english.suggestCalls)
    }

    @Test
    fun `autocorrect is granted to a confident primary top suggestion`() {
        val spanish = FakeEngine(spanishVocabulary, listOf(s("hola", 200, auto = true)))
        val english = FakeEngine(englishVocabulary, listOf(s("hold", 100)))
        val result = engine(spanish, english).suggest(emptyList(), "hoal", es)
        assertTrue(result.first().autoCorrect)
    }

    @Test
    fun `autocorrect is withheld when language confidence is low`() {
        val spanish = FakeEngine(spanishVocabulary, listOf(s("casa", 200, auto = true)))
        val english = FakeEngine(englishVocabulary)
        // one English word right before: Spanish drops to 0.375, below the 0.7 gate
        val result = engine(spanish, english).suggest(listOf("hello"), "csa", es)
        assertEquals("casa", result.first().word)
        assertFalse(result.first().autoCorrect)
    }

    @Test
    fun `autocorrect for English needs enough English context`() {
        val spanish = FakeEngine(spanishVocabulary)
        val english = FakeEngine(englishVocabulary, listOf(s("house", 200, auto = true)))
        val mixed = engine(spanish, english)
        assertFalse(mixed.suggest(listOf("hello"), "hose", es).first().autoCorrect)
        assertTrue(mixed.suggest(listOf("the", "hello"), "hose", es).first().autoCorrect)
    }

    @Test
    fun `a word valid in the other language is never autocorrected`() {
        val spanish = FakeEngine(spanishVocabulary, listOf(s("perro", 200, auto = true)))
        val english = FakeEngine(englishVocabulary)
        val result = engine(spanish, english).suggest(emptyList(), "dog", es)
        assertFalse(result.first().autoCorrect)
    }

    @Test
    fun `only the top suggestion can be autocorrected and the engine must have asked for it`() {
        val spanish =
            FakeEngine(
                spanishVocabulary,
                listOf(s("hola", 200, auto = true), s("hora", 150, auto = true))
            )
        val result = engine(spanish, FakeEngine()).suggest(emptyList(), "hoal", es)
        assertEquals(listOf(true, false), result.map { it.autoCorrect })

        val noRequest = FakeEngine(spanishVocabulary, listOf(s("hola", 200, auto = false)))
        assertFalse(
            engine(noRequest, FakeEngine()).suggest(emptyList(), "hoal", es).first().autoCorrect
        )
    }

    @Test
    fun `empty composing or identical word is never autocorrected`() {
        val spanish = FakeEngine(spanishVocabulary, listOf(s("hola", 200, auto = true)))
        val mixed = engine(spanish, FakeEngine())
        assertFalse(mixed.suggest(emptyList(), "", es).first().autoCorrect)
        assertFalse(mixed.suggest(emptyList(), "hola", es).first().autoCorrect)
    }

    @Test
    fun `no candidates gives an empty list`() {
        assertTrue(engine(FakeEngine(), FakeEngine()).suggest(emptyList(), "x", es).isEmpty())
    }

    @Test
    fun `next word prediction merges by weight and never autocorrects`() {
        val spanish =
            FakeEngine(spanishVocabulary, predictions = listOf(s("casa", 100, auto = true)))
        val english = FakeEngine(englishVocabulary, predictions = listOf(s("house", 100)))
        val mixed = engine(spanish, english)
        assertEquals(listOf("casa", "house"), mixed.predictNext(emptyList(), es).map { it.word })
        val inEnglish = mixed.predictNext(listOf("the", "hello"), es)
        assertEquals("house", inEnglish.first().word)
        assertTrue(inEnglish.none { it.autoCorrect })
    }

    @Test
    fun `a word is valid when any language accepts it`() {
        val mixed = engine(FakeEngine(spanishVocabulary), FakeEngine(englishVocabulary))
        assertTrue(mixed.isValidWord("hola", en))
        assertTrue(mixed.isValidWord("dog", es))
        assertFalse(mixed.isValidWord("zzzz", es))
    }

    @Test
    fun `learn goes to the only language that knows the word`() {
        val spanish = FakeEngine(spanishVocabulary)
        val english = FakeEngine(englishVocabulary)
        engine(spanish, english).learn("dog", listOf("hola"), es)
        assertTrue(spanish.learned.isEmpty())
        assertEquals(listOf(Triple("dog", listOf("hola"), en)), english.learned)
    }

    @Test
    fun `learn of an unknown or shared word follows the context`() {
        val spanish = FakeEngine(spanishVocabulary)
        val english = FakeEngine(englishVocabulary)
        val mixed = engine(spanish, english)
        mixed.learn("zzzz", emptyList(), en) // no evidence: primary
        mixed.learn("no", listOf("the", "hello"), es) // valid in both: English context
        assertEquals(listOf("zzzz"), spanish.learned.map { it.first })
        assertEquals(listOf("no"), english.learned.map { it.first })
    }

    @Test
    fun `user dictionary additions go to the matching language or the primary`() {
        val spanish = FakeEngine()
        val english = FakeEngine()
        val mixed = engine(spanish, english)
        mixed.addToUserDictionary("qtek", en)
        mixed.addToUserDictionary("fulano", es)
        mixed.addToUserDictionary("bonjour", Locale.FRENCH)
        assertEquals(listOf("qtek"), english.added)
        assertEquals(listOf("fulano", "bonjour"), spanish.added)
    }

    @Test
    fun `user dictionary removal and clearing fan out to every engine`() {
        val spanish = FakeEngine()
        val english = FakeEngine()
        val mixed = engine(spanish, english)
        mixed.removeFromUserDictionary("x", es)
        mixed.clearLearned()
        assertEquals(listOf("x"), spanish.removed)
        assertEquals(listOf("x"), english.removed)
        assertEquals(1, spanish.cleared)
        assertEquals(1, english.cleared)
    }

    @Test
    fun `user dictionary listing is per language, or merged for unknown languages`() {
        val spanish = FakeEngine().apply { userWords = listOf("b", "a") }
        val english = FakeEngine().apply { userWords = listOf("a", "c") }
        val mixed = engine(spanish, english)
        assertEquals(listOf("b", "a"), mixed.userDictionaryWords(Locale.forLanguageTag("es-MX")))
        assertEquals(listOf("a", "c"), mixed.userDictionaryWords(en))
        assertEquals(listOf("a", "b", "c"), mixed.userDictionaryWords(Locale.FRENCH))
    }

    @Test
    fun `close reaches every engine even when one fails`() {
        val failing = FakeEngine(closeFailure = IllegalStateException("boom"))
        val other = FakeEngine(closeFailure = IllegalStateException("second"))
        val healthy = FakeEngine()
        val mixed =
            MixedSuggestionEngine(
                mapOf(
                    es to failing,
                    en to healthy,
                    Locale.forLanguageTag("fr") to other
                ),
                es
            )
        val thrown = assertThrows(IllegalStateException::class.java) { mixed.close() }
        assertEquals(1, failing.closed)
        assertEquals(1, healthy.closed)
        assertEquals(1, other.closed)
        assertEquals(1, thrown.suppressed.size)
    }

    @Test
    fun `a single engine behaves like a pass-through with full confidence`() {
        val spanish = FakeEngine(spanishVocabulary, listOf(s("hola", 10, auto = true)))
        val mixed = MixedSuggestionEngine(mapOf(es to spanish))
        assertEquals(mapOf("es" to 1.0), mixed.languageWeights(listOf("hello")))
        assertTrue(mixed.suggest(emptyList(), "hoal", es).first().autoCorrect)
    }

    @Test
    fun `three languages split the remaining prior evenly`() {
        val mixed = MixedSuggestionEngine(
            mapOf(
                es to FakeEngine(),
                en to FakeEngine(),
                Locale.forLanguageTag("fr") to FakeEngine()
            ),
            es
        )
        val weights = mixed.languageWeights(emptyList())
        assertEquals(0.125, weights.getValue("en"), 1e-9)
        assertEquals(0.125, weights.getValue("fr"), 1e-9)
    }

    @Test
    fun `construction is validated`() {
        assertThrows(IllegalArgumentException::class.java) { MixedSuggestionEngine(emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) {
            MixedSuggestionEngine(mapOf(en to FakeEngine()), es)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MixedSuggestionEngine(
                mapOf(
                    Locale.forLanguageTag("en-US") to FakeEngine(),
                    Locale.forLanguageTag("en-GB") to FakeEngine()
                ),
                en
            )
        }
        listOf(
            { MixedConfig(contextWords = -1) },
            { MixedConfig(primaryPrior = 1.5) },
            { MixedConfig(recencyDecay = 0.0) },
            { MixedConfig(priorMass = 0.0) },
            { MixedConfig(autoCorrectConfidence = 2.0) },
            { MixedConfig(maxResults = 0) }
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }
}
