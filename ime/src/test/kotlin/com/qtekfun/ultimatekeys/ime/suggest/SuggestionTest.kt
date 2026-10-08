// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.suggest

import com.qtekfun.ultimatekeys.engine.NoopSuggestionEngine
import com.qtekfun.ultimatekeys.engine.Suggestion
import com.qtekfun.ultimatekeys.engine.SuggestionEngine
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FakeEngine(
    var suggestions: (List<String>, String) -> List<Suggestion> = { _, _ -> emptyList() },
    var next: (List<String>) -> List<Suggestion> = { emptyList() }
) : SuggestionEngine by NoopSuggestionEngine {
    val learned = mutableListOf<Pair<String, List<String>>>()

    override fun suggest(context: List<String>, composing: String, locale: Locale) =
        suggestions(context, composing)

    override fun predictNext(context: List<String>, locale: Locale) = next(context)

    override fun learn(word: String, context: List<String>, locale: Locale) {
        learned += word to context
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionTest {
    @Test
    fun `context words come from the current sentence only`() {
        assertEquals(listOf("hola", "me", "llamo"), ContextWords.from("Adiós. hola me llamo "))
        assertEquals(emptyList<String>(), ContextWords.from("Qué tal? "))
        assertEquals(listOf("it's", "me"), ContextWords.from("it's me"))
        assertEquals(4, ContextWords.from("a b c d e f g").size)
    }

    @Test
    fun `case follows what the user typed`() {
        assertEquals("hola", CaseMatcher.match("hol", "hola"))
        assertEquals("Hola", CaseMatcher.match("Hol", "hola"))
        assertEquals("HOLA", CaseMatcher.match("HOL", "hola"))
        assertEquals("Hola", CaseMatcher.match("H", "hola"))
        assertEquals("hola", CaseMatcher.match("", "hola"))
    }

    @Test
    fun `strip shows best in the centre and keeps the typed word when correcting`() = runTest {
        val engine = FakeEngine(suggestions = { _, c ->
            if (c ==
                "teh"
            ) {
                listOf(Suggestion("the", 9, autoCorrect = true), Suggestion("ten", 4))
            } else {
                listOf(Suggestion("hola", 9), Suggestion("holas", 5), Suggestion("holar", 2))
            }
        })
        val dispatcher = StandardTestDispatcher(testScheduler)
        val c = SuggestionController(engine, backgroundScope, dispatcher, { Locale.ENGLISH })
        c.onComposingChanged("hol", "")
        runCurrent()
        assertEquals(listOf("holas", "hola", "holar"), c.state.value.slots)
        c.onComposingChanged("teh", "")
        runCurrent()
        assertEquals(listOf("teh", "the", "ten"), c.state.value.slots)
        assertEquals("the", c.autoCorrectFor("teh"))
        assertNull(c.autoCorrectFor("other"))
    }

    @Test
    fun `autocorrect keeps the capitalization and stale results are ignored`() = runTest {
        val engine =
            FakeEngine(suggestions = { _, _ -> listOf(Suggestion("the", 9, autoCorrect = true)) })
        val c =
            SuggestionController(engine, backgroundScope, StandardTestDispatcher(testScheduler), {
                Locale.ENGLISH
            })
        c.onComposingChanged("Teh", "")
        runCurrent()
        assertEquals("The", c.autoCorrectFor("Teh"))
        assertNull(c.autoCorrectFor("Te"))
    }

    @Test
    fun `next word prediction runs when nothing is composed`() = runTest {
        val engine = FakeEngine(next = { ctx ->
            if (ctx ==
                listOf("buenos")
            ) {
                listOf(Suggestion("días", 9))
            } else {
                emptyList()
            }
        })
        val c =
            SuggestionController(engine, backgroundScope, StandardTestDispatcher(testScheduler), {
                Locale.ENGLISH
            })
        c.onComposingChanged("", "buenos ")
        runCurrent()
        assertEquals(listOf("", "días", ""), c.state.value.slots)
        c.onComposingChanged("", "nada ")
        runCurrent()
        assertEquals(SuggestionState().slots, c.state.value.slots)
    }

    @Test
    fun `learning respects the privacy switch`() = runTest {
        val engine = FakeEngine()
        var allowed = true
        val c =
            SuggestionController(engine, backgroundScope, StandardTestDispatcher(testScheduler), {
                Locale.ENGLISH
            }) { allowed }
        c.onWordFinished("hola", "buenos ", corrected = false)
        runCurrent()
        assertEquals(listOf("hola" to listOf("buenos")), engine.learned)
        allowed = false
        c.onWordFinished("secreto", "", corrected = false)
        c.onAutoCorrectRejected("secreto")
        c.onWordFinished(" ", "", corrected = false)
        runCurrent()
        assertEquals(1, engine.learned.size)
        allowed = true
        c.onAutoCorrectRejected("xyz")
        runCurrent()
        assertEquals("xyz", engine.learned.last().first)
    }
}
