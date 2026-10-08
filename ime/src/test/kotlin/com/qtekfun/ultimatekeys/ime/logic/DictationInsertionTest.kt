// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.logic

import android.text.InputType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DictationFormatterTest {
    private fun format(
        raw: String,
        before: String = "",
        after: String = "",
        language: String = "en"
    ) = DictationFormatter.format(raw, before, after, language, prose = true)

    @Test
    fun `empty field starts with a capital and no space`() {
        assertEquals("Hello there.", format(" hello there. "))
    }

    @Test
    fun `after a sentence end a space and a capital`() {
        assertEquals(" Next one.", format("next one.", before = "First."))
        assertEquals("Next one.", format("next one.", before = "First. "))
        assertEquals("Next one.", format("next one.", before = "First line\n"))
        assertEquals(" ¿Qué tal?", format("¿qué tal?", before = "Hola.", language = "es"))
    }

    @Test
    fun `in the middle of a sentence a function word is lowercased`() {
        assertEquals(" and then we left.", format("And then we left.", before = "We came"))
        assertEquals("el perro", format("El perro", before = "Vi a ", language = "es"))
    }

    @Test
    fun `names, acronyms and I keep their capitals mid sentence`() {
        assertEquals(" John said hi.", format("John said hi.", before = "Then"))
        assertEquals(" NASA called.", format("NASA called.", before = "Then"))
        assertEquals(" I agree.", format("I agree.", before = "Then"))
    }

    @Test
    fun `no extra space after whitespace, an opener or before punctuation`() {
        assertEquals("the", format("The", before = "to "))
        assertEquals("quote", format("quote", before = "He said \""))
        assertEquals(", and so on", format(", and so on", before = "etc"))
        assertEquals("cómo", format("cómo", before = "¿", language = "es"))
    }

    @Test
    fun `a space after the text when a word follows the cursor`() {
        assertEquals("Hello ", format("Hello", after = "world"))
        assertEquals("Hello", format("Hello", after = " world"))
        assertEquals("Hello", format("Hello", after = ", world"))
        assertEquals("Hello", format("Hello", after = ""))
    }

    @Test
    fun `fields that are not prose keep the words as heard`() {
        assertEquals(
            "Name",
            DictationFormatter.format("Name", "x@", "", "en", prose = false)
        )
    }

    @Test
    fun `nothing in, nothing out`() {
        assertEquals("", format("   "))
        assertEquals("123", format("123"))
    }
}

class DictationInsertionTest {
    private val hook = RecordingHook()

    private class RecordingHook : SuggestionHook {
        val words = mutableListOf<Pair<String, String>>()
        var composingResets = 0

        override fun onComposingChanged(composing: String, contextBefore: String) {
            if (composing.isEmpty()) composingResets++
        }

        override fun autoCorrectFor(composing: String): String? = null

        override fun onWordFinished(word: String, contextBefore: String, corrected: Boolean) {
            words += word to contextBefore
        }

        override fun onAutoCorrectRejected(original: String) = Unit
    }

    private fun rig(initial: String = ""): Pair<InputLogic, FakeEditorConnection> {
        val editor = FakeEditorConnection(initial)
        val logic = InputLogic()
        logic.suggestionHook = hook
        logic.onStartInput(
            editor,
            EditorContext.from(InputType.TYPE_CLASS_TEXT, 0),
            restarting = false,
            initialCursor = editor.cursor
        )
        return logic to editor
    }

    @Test
    fun `inserts in one commit inside one batch`() {
        val (logic, editor) = rig("Hi.")
        var commits = 0
        val counting = object : EditorConnection by editor {
            override fun commitText(text: CharSequence) {
                commits++
                editor.commitText(text)
            }
        }
        logic.onStartInput(counting, EditorContext(), restarting = true, initialCursor = 3)
        logic.insertDictation("how are you?", "en", learn = false)
        assertEquals("Hi. How are you?", editor.text.toString())
        assertEquals(1, commits)
        assertEquals(0, editor.batchDepth)
    }

    @Test
    fun `replaces a selection`() {
        val (logic, editor) = rig("Hello brave world")
        editor.setSelection(6, 11)
        logic.insertDictation("new", "en", learn = false)
        assertEquals("Hello new world", editor.text.toString())
    }

    @Test
    fun `finishes the word being composed first`() {
        val editor = FakeEditorConnection()
        val logic = InputLogic()
        logic.options = InputOptions(composeWords = true)
        logic.onStartInput(editor, EditorContext(), restarting = false, initialCursor = 0)
        "hol".forEach { logic.onText(it.toString()) }
        logic.insertDictation("and then", "en", learn = false)
        assertEquals("hol and then", editor.text.toString())
        assertEquals("", editor.composingText)
    }

    @Test
    fun `learns the words only when asked to`() {
        val (quiet, _) = rig("Hello ")
        quiet.insertDictation("good morning, world!", "en", learn = false)
        assertTrue(hook.words.isEmpty())
        val (logic, _) = rig("Hello ")
        logic.insertDictation("good morning, world!", "en", learn = true)
        assertEquals(listOf("good", "morning", "world"), hook.words.map { it.first })
        assertEquals("Hello ", hook.words[0].second)
        assertEquals("Hello good ", hook.words[1].second)
    }

    @Test
    fun `does nothing without a field or without text`() {
        val logic = InputLogic()
        logic.insertDictation("hello", "en", learn = true)
        val (inField, editor) = rig("abc")
        inField.insertDictation("  ", "en", learn = true)
        assertEquals("abc", editor.text.toString())
        assertEquals(0, editor.batchDepth)
    }

    @Test
    fun `fields that are not prose get the words untouched`() {
        val editor = FakeEditorConnection()
        val logic = InputLogic()
        logic.onStartInput(
            editor,
            EditorContext.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, 0),
            restarting = false,
            initialCursor = 0
        )
        logic.insertDictation("example dot com", "en", learn = false)
        assertEquals("example dot com", editor.text.toString())
    }
}
