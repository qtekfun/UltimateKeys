// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

import java.nio.file.Files
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DictionaryBuilderTest {
    private val dir = Files.createTempDirectory("dict").toFile()

    @Test
    fun `builds a dictionary from a word list`() {
        val bridge = FakeNativeBridge()
        val target = dir.resolve("es")
        val ok = DictionaryBuilder(bridge) {}.build(
            target,
            Locale.forLanguageTag("es"),
            sequenceOf(
                WordFrequency("hola", 200),
                WordFrequency(" ", 5),
                WordFrequency("mundo", 999)
            )
        )
        assertTrue(ok)
        assertTrue(target.isDirectory)
    }

    @Test
    fun `an empty list is a failure and leaves nothing behind`() {
        val target = dir.resolve("en")
        assertFalse(
            DictionaryBuilder(FakeNativeBridge()) {}.build(target, Locale.ENGLISH, emptySequence())
        )
        assertFalse(target.exists())
    }

    @Test
    fun `the swappable engine delegates and closes the old one`() {
        val first = SpyEngine()
        val second = SpyEngine()
        val swappable = SwappableSuggestionEngine(first)
        swappable.learn("a", emptyList(), Locale.ENGLISH)
        assertEquals(1, first.learned)
        swappable.swap(second)
        assertTrue(first.closed)
        swappable.learn("b", emptyList(), Locale.ENGLISH)
        assertEquals(1, second.learned)
        assertSame(
            emptyList<Suggestion>(),
            NoopSuggestionEngine.suggest(emptyList(), "x", Locale.ENGLISH)
        )
    }

    private class SpyEngine : SuggestionEngine by NoopSuggestionEngine {
        var learned = 0
        var closed = false

        override fun learn(word: String, context: List<String>, locale: Locale) {
            learned++
        }

        override fun close() {
            closed = true
        }
    }
}
