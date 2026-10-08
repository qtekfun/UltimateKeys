// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatekeys.dictionaries.BinaryDictionaries
import com.qtekfun.ultimatekeys.dictionaries.WordListParser
import com.qtekfun.ultimatekeys.dictionaries.installDictionaries
import com.qtekfun.ultimatekeys.engine.AospSuggestionEngine
import com.qtekfun.ultimatekeys.engine.DictionaryBuilder
import com.qtekfun.ultimatekeys.ime.suggest.LayoutGeometries
import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Builds the real dictionaries through the native engine and checks lookups, suggestions and latency. */
@RunWith(AndroidJUnit4::class)
class EngineJniTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "engine-jni-test")
    private val es = Locale.forLanguageTag("es")
    private val en = Locale.forLanguageTag("en")
    private val fr = Locale.forLanguageTag("fr")
    private val ru = Locale.forLanguageTag("ru")
    private val tr = Locale.forLanguageTag("tr")
    private val el = Locale.forLanguageTag("el")
    private val ro = Locale.forLanguageTag("ro")
    private val languages = listOf("es", "en-US", "fr", "ru", "tr", "el", "ro")
    private lateinit var engine: AospSuggestionEngine

    @Before
    fun setUp() {
        root.deleteRecursively()
        val messages = mutableListOf<String>()
        val builder = DictionaryBuilder { messages += it }
        val dictionaries = BinaryDictionaries(
            context.installDictionaries(languages.toSet()),
            File(root, "dictionaries"),
            build = builder::build
        )
        val ready = dictionaries.prepare(languages)
        // Latin with accents, Cyrillic, Greek, Turkish dotted i and a list over the word cap must all build.
        assertEquals("dictionary build failed: ${messages.takeLast(5)}", languages.toSet(), ready)
        val locator = dictionaries.locator()
        assertTrue(locator.mainDictionary(en) != null)
        engine = AospSuggestionEngine(
            File(root, "learned"),
            locator,
            geometryFor = { LayoutGeometries.forLocale(it) }
        )
    }

    @After
    fun tearDown() {
        engine.close()
        root.deleteRecursively()
    }

    @Test
    fun knowsWordsOfBothLanguages() {
        assertTrue(engine.isValidWord("hola", es))
        assertTrue(engine.isValidWord("house", en))
        assertFalse(engine.isValidWord("qzxwv", en))
    }

    @Test
    fun knowsWordsOfOtherScriptsAndAccents() {
        assertTrue(engine.isValidWord("été", fr))
        assertTrue(engine.isValidWord("привет", ru))
        assertTrue(engine.isValidWord("ηλιος", el) || engine.isValidWord("ήλιος", el))
        assertTrue(engine.isValidWord("çocuk", tr))
        assertTrue(engine.isValidWord("și", ro))
        assertFalse(engine.isValidWord("hello", ru))
    }

    // The most frequent words of a language (letters only, at least six of them): what people type most.
    private fun commonWords(locale: Locale, count: Int = COMMON_SAMPLE): List<String> {
        val file = checkNotNull(context.installDictionaries(languages.toSet()).fileFor(locale))
        val list = file.inputStream().use { WordListParser.parseGzip(it) }
        return list.words.asSequence().filter { !it.notAWord && !it.possiblyOffensive }
            .map { it.word }
            .filter { w ->
                w.length >= MIN_WORD && w.all { it.isLetter() } &&
                    w == w.lowercase(locale)
            }
            .take(count).toList()
    }

    private fun hitRate(locale: Locale, words: List<String>, typed: (String) -> String): Double {
        val hits = words.count { word ->
            engine.suggest(emptyList(), typed(word), locale).any { it.word.equals(word, true) }
        }
        println("UKEngine ${locale.toLanguageTag()} hit rate $hits/${words.size}")
        return hits.toDouble() / words.size
    }

    private fun assertCompletes(locale: Locale) {
        val rate = hitRate(locale, commonWords(locale)) { it.dropLast(1) }
        assertTrue("${locale.toLanguageTag()} completion hit rate $rate", rate >= COMPLETION_RATE)
    }

    // Prefix completion of common words, one test per script so a doubtful one cannot hide the others.
    @Test
    fun completesCommonCyrillicWords() = assertCompletes(ru)

    @Test
    fun completesCommonGreekWords() = assertCompletes(el)

    @Test
    fun completesCommonTurkishWords() = assertCompletes(tr)

    @Test
    fun completesCommonFrenchWords() = assertCompletes(fr)

    @Test
    fun completesCommonRomanianWords() = assertCompletes(ro)

    @Test
    fun frenchWithoutAccentsFindsTheAccentedWord() {
        val accented = commonWords(fr, ACCENT_POOL).filter { w -> w.any { it in "éèêàçùô" } }
            .take(COMMON_SAMPLE)
        val rate = hitRate(fr, accented) { w ->
            java.text.Normalizer.normalize(w, java.text.Normalizer.Form.NFD)
                .filter { it.code < ASCII }
        }
        assertTrue("accent-less French hit rate $rate", rate >= ACCENT_RATE)
    }

    @Test
    fun frenchEcoleBecomesEcole() {
        assertTrue(engine.suggest(emptyList(), "ecole", fr).any { it.word == "école" })
    }

    // A doubtful assertion on purpose kept apart: correcting a swapped pair of letters without coordinates.
    @Test
    fun swappedLettersInACyrillicWordAreCorrected() {
        val rate = hitRate(ru, commonWords(ru)) { w ->
            val i = w.length / 2
            w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2)
        }
        assertTrue("Cyrillic swapped letters hit rate $rate", rate >= TYPO_RATE)
    }

    @Test
    fun suggestsCompletionsAndCorrections() {
        assertTrue(engine.suggest(emptyList(), "hous", en).any { it.word == "house" })
        assertTrue(engine.suggest(emptyList(), "ola", es).any { it.word == "hola" })
    }

    @Test
    fun learnsUserWords() {
        engine.addToUserDictionary("ukzorblax", en)
        assertEquals(listOf("ukzorblax"), engine.userDictionaryWords(en))
        engine.clearLearned()
        assertTrue(engine.userDictionaryWords(en).isEmpty())
    }

    @Test
    fun suggestionLatencyStaysUnderTarget() {
        val prefixes = listOf("th", "hou", "wor", "qu", "bet", "int", "pro", "com")
        repeat(WARMUP) { engine.suggest(listOf("the"), prefixes[it % prefixes.size], en) }
        val millis = List(SAMPLES) {
            val start = System.nanoTime()
            engine.suggest(listOf("the"), prefixes[it % prefixes.size], en)
            (System.nanoTime() - start) / NANOS_PER_MILLI
        }.sorted()
        val p95 = millis[(SAMPLES * P95_FRACTION).toInt()]
        println("UKLatency engine suggest p50=${millis[SAMPLES / 2]}ms p95=${p95}ms")
        assertTrue("suggest p95 was $p95 ms, target is 30 ms", p95 < TARGET_MS)
    }

    private companion object {
        const val WARMUP = 20
        const val COMMON_SAMPLE = 200
        const val ACCENT_POOL = 3000
        const val MIN_WORD = 6
        const val ASCII = 128
        const val COMPLETION_RATE = 0.8
        const val ACCENT_RATE = 0.7
        const val TYPO_RATE = 0.5
        const val SAMPLES = 200
        const val P95_FRACTION = 0.95
        const val NANOS_PER_MILLI = 1_000_000.0
        const val TARGET_MS = 30.0
    }
}
