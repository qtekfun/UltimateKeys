// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

import java.io.File
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AospSuggestionEngineTest {
    @TempDir
    lateinit var storage: File

    private val english = Locale.ENGLISH
    private val spanish = Locale.forLanguageTag("es")
    private val mainPath = "/dict/main_en.dict"
    private lateinit var bridge: FakeNativeBridge
    private var learning = true
    private var nowMillis = 5_000_000L

    private fun typing(
        vararg results: RawSuggestion
    ): (String, List<String>) -> List<RawSuggestion> = { _, _ -> results.toList() }

    private fun engine(withMain: Boolean = true, threshold: Float = 0.185f) = AospSuggestionEngine(
        storageDir = storage,
        dictionaries = { if (withMain) DictionaryFile(mainPath, 0L, 1000L) else null },
        geometryFor = KeyboardGeometry::qwertyFor,
        learningEnabled = { learning },
        autoCorrectThreshold = threshold,
        clockMillis = { nowMillis },
        bridge = bridge
    )

    @BeforeEach
    fun setUp() {
        bridge = FakeNativeBridge()
        bridge.mainScripts[mainPath] = { dict ->
            dict.probabilities["hello"] = 200
            dict.probabilities["help"] = 150
        }
    }

    @Test
    fun `suggest merges dictionaries keeps best score per word and sorts`() {
        bridge.mainScripts[mainPath] = { dict ->
            dict.typingResults =
                typing(RawSuggestion("hello", 100, 1), RawSuggestion("help", 300, 1))
        }
        val engine = engine()
        engine.suggest(emptyList(), "hel", english) // opens everything
        bridge.dictAt("history-en").typingResults =
            typing(RawSuggestion("Hello", 500, 1), RawSuggestion("hell", 50, 1))

        val result = engine.suggest(emptyList(), "hel", english)

        assertEquals(listOf("Hello", "help", "hell"), result.map { it.word })
        assertEquals(listOf(500, 300, 50), result.map { it.score })
        assertTrue(result.all { it.locale == english })
    }

    @Test
    fun `candidates take the capitalization of what was typed`() {
        bridge.mainScripts[mainPath] = { it.typingResults = typing(RawSuggestion("hello", 100, 1)) }
        val engine = engine()

        assertEquals("Hello", engine.suggest(emptyList(), "Hel", english).single().word)
        assertEquals("HELLO", engine.suggest(emptyList(), "HEL", english).single().word)
        assertEquals("hello", engine.suggest(emptyList(), "hel", english).single().word)
    }

    @Test
    fun `typo is autocorrected when the word is unknown and the score is high enough`() {
        bridge.mainScripts[mainPath] = { dict ->
            dict.typingResults = typing(
                RawSuggestion("hello", 900, FakeNativeBridge.AUTOCORRECT_OK),
                RawSuggestion("help", 100, FakeNativeBridge.AUTOCORRECT_OK)
            )
        }
        bridge.normalizedScore = 0.6f

        val result = engine().suggest(emptyList(), "hwllo", english)

        assertTrue(result[0].autoCorrect)
        assertFalse(result[1].autoCorrect)
    }

    @Test
    fun `no autocorrect below the threshold, for known words, short input or flagged candidates`() {
        val flagged = RawSuggestion("hello", 900, FakeNativeBridge.AUTOCORRECT_OK)
        bridge.mainScripts[mainPath] = { dict ->
            dict.probabilities["hel"] = 10
            dict.typingResults = { typed, _ ->
                listOf(if (typed == "hwllo") RawSuggestion("hello", 900, 1) else flagged)
            }
        }
        val engine = engine()

        bridge.normalizedScore = 0.1f
        assertFalse(engine.suggest(emptyList(), "hwllo", english)[0].autoCorrect, "below threshold")
        bridge.normalizedScore = 0.9f
        assertFalse(
            engine.suggest(emptyList(), "hwllo", english)[0].autoCorrect,
            "not appropriate for autocorrect"
        )
        assertFalse(
            engine.suggest(emptyList(), "hel", english)[0].autoCorrect,
            "typed word is in the dictionary"
        )
        assertFalse(engine.suggest(emptyList(), "h", english)[0].autoCorrect, "too short")
        assertTrue(engine.suggest(emptyList(), "hx", english)[0].autoCorrect)
    }

    @Test
    fun `predictNext returns sorted predictions that are never autocorrected`() {
        bridge.mainScripts[mainPath] = { dict ->
            dict.predictionResults = { context ->
                assertEquals(listOf("hi", "how", "are"), context) // last three words, oldest first
                listOf(
                    RawSuggestion("you", 90, RawSuggestion.KIND_PREDICTION),
                    RawSuggestion("they", 200, RawSuggestion.KIND_PREDICTION)
                )
            }
        }

        val result = engine().predictNext(listOf("so", "hi", "how", "are"), english)

        assertEquals(listOf("they", "you"), result.map { it.word })
        assertTrue(result.none { it.autoCorrect })
    }

    @Test
    fun `works without a main dictionary using only learned data`() {
        val engine = engine(withMain = false)
        engine.suggest(emptyList(), "ab", english)
        bridge.dictAt("history-en").typingResults = typing(RawSuggestion("abc", 10, 1))

        assertEquals(listOf("abc"), engine.suggest(emptyList(), "ab", english).map { it.word })
        assertFalse(engine.isValidWord("hello", english))
    }

    @Test
    fun `isValidWord checks the dictionaries and the lower case form`() {
        val engine = engine()

        assertTrue(engine.isValidWord("hello", english))
        assertTrue(engine.isValidWord("Hello", english))
        assertFalse(engine.isValidWord("hwllo", english))
        assertFalse(engine.isValidWord("", english))
    }

    @Test
    fun `learn records the word with the last three words of context`() {
        val engine = engine()

        engine.learn("hello", listOf("a", "b", "c", "d"), english)

        val history = bridge.dictAt("history-en")
        assertEquals(listOf(Triple(listOf("b", "c", "d"), "hello", true)), history.learned)
    }

    @Test
    fun `learn marks unknown words as not valid and skips junk`() {
        val engine = engine()

        engine.learn("zzyzx", emptyList(), english)
        engine.learn("two words", emptyList(), english)
        engine.learn("1234", emptyList(), english)
        engine.learn("", emptyList(), english)

        assertEquals(
            listOf(Triple(emptyList<String>(), "zzyzx", false)),
            bridge.dictAt("history-en").learned
        )
    }

    @Test
    fun `learn does nothing while learning is disabled`() {
        val engine = engine()
        learning = false

        engine.learn("hello", emptyList(), english)

        assertTrue(bridge.dictionaries.values.none { it.learned.isNotEmpty() })
    }

    @Test
    fun `learned data is flushed periodically and on close`() {
        val engine = engine()
        repeat(9) { engine.learn("hello", emptyList(), english) }
        val history = bridge.dictAt("history-en")
        assertEquals(0, history.flushes)

        engine.learn("hello", emptyList(), english)
        assertEquals(1, history.flushes)

        engine.learn("hello", emptyList(), english)
        engine.close()
        assertEquals(2, history.flushes)
        assertTrue(history.closed)
    }

    @Test
    fun `user dictionary add list remove`() {
        val engine = engine()

        engine.addToUserDictionary("  Zebra ", english)
        engine.addToUserDictionary("apple", english)
        engine.addToUserDictionary("not a word", english)
        engine.addToUserDictionary("", english)

        assertEquals(listOf("apple", "Zebra"), engine.userDictionaryWords(english))
        assertTrue(engine.isValidWord("apple", english))
        assertTrue(bridge.dictAt("user-en").flushes >= 1)

        engine.removeFromUserDictionary("apple", english)
        assertEquals(listOf("Zebra"), engine.userDictionaryWords(english))
        assertFalse(engine.isValidWord("apple", english))
        assertTrue(engine.userDictionaryWords(spanish).isEmpty())
    }

    @Test
    fun `clearLearned deletes learned and user data and starts fresh`() {
        val engine = engine()
        engine.addToUserDictionary("zebra", english)
        engine.learn("hello", emptyList(), english)
        val oldHistory = bridge.dictAt("history-en")
        assertTrue(File(storage, "history-en").exists())

        engine.clearLearned()

        assertTrue(oldHistory.closed)
        assertFalse(File(storage, "history-en").exists())
        assertEquals(emptyList<String>(), engine.userDictionaryWords(english))
        assertTrue(File(storage, "history-en").exists())
        assertTrue(bridge.dictAt("history-en").learned.isEmpty())
        assertTrue(engine.isValidWord("hello", english), "main dictionary is untouched")
    }

    @Test
    fun `a broken learned dictionary is recreated`() {
        File(storage, "history-en").mkdirs() // directory without a readable header
        val engine = engine()

        engine.learn("hello", emptyList(), english)

        assertEquals(1, bridge.dictAt("history-en").learned.size)
    }

    @Test
    fun `close releases native resources and later calls are harmless`() {
        val engine = engine()
        engine.suggest(emptyList(), "hel", english)
        engine.suggest(emptyList(), "hol", spanish)

        engine.close()

        assertEquals(0, bridge.liveSessions)
        assertEquals(0, bridge.liveProximityInfos)
        assertTrue(bridge.dictionaries.values.all { it.closed })
        assertTrue(engine.suggest(emptyList(), "hel", english).isEmpty())
        assertTrue(engine.predictNext(emptyList(), english).isEmpty())
        assertFalse(engine.isValidWord("hello", english))
        engine.learn("hello", emptyList(), english)
        engine.close()
    }

    @Test
    fun `languages are independent`() {
        val engine = engine()
        engine.addToUserDictionary("zebra", english)

        assertTrue(engine.userDictionaryWords(spanish).isEmpty())
        assertTrue(File(storage, "user-en").isDirectory)
    }
}
