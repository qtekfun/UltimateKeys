// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.logic

import android.text.InputType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** How a word typed by gesture lands in the editor, and how it is taken back or swapped. */
class GestureCommitTest {
    private class Hook : SuggestionHook {
        val finished = mutableListOf<Pair<String, String>>()
        val gestures = mutableListOf<Triple<String, String, List<String>>>()
        val composing = mutableListOf<String>()

        override fun onComposingChanged(composing: String, contextBefore: String) {
            this.composing += composing
        }

        override fun autoCorrectFor(composing: String): String? = null

        override fun onWordFinished(word: String, contextBefore: String, corrected: Boolean) {
            finished += word to contextBefore
        }

        override fun onAutoCorrectRejected(original: String) = Unit

        override fun onGestureCommitted(
            word: String,
            contextBefore: String,
            alternatives: List<String>
        ) {
            gestures += Triple(word, contextBefore, alternatives)
        }
    }

    private val editor = FakeEditorConnection()
    private val hook = Hook()
    private val logic = InputLogic { 1_000L }.also {
        it.options = InputOptions(composeWords = true)
        it.suggestionHook = hook
        it.onStartInput(
            editor,
            EditorContext.from(InputType.TYPE_CLASS_TEXT, 0),
            restarting = false,
            initialCursor = 0
        )
    }

    private val text get() = editor.text.toString()

    @Test
    fun `a gesture word is followed by a space`() {
        assertTrue(logic.commitGestureWord("hola", listOf("hora")))
        assertEquals("hola ", text)
        assertEquals(0, editor.batchDepth)
        assertEquals(Triple("hola", "hola ", listOf("hora")), hook.gestures.single())
    }

    @Test
    fun `consecutive gesture words are separated by one space`() {
        logic.commitGestureWord("hola")
        logic.commitGestureWord("mundo")
        assertEquals("hola mundo ", text)
    }

    @Test
    fun `a word typed by hand gets a space before the gesture word`() {
        "hol".forEach { logic.onText(it.toString()) }
        logic.commitGestureWord("mundo")
        assertEquals("hol mundo ", text)
        assertEquals("hol" to "", hook.finished.single())
    }

    @Test
    fun `no extra space after a space, an opening bracket or at the start`() {
        logic.onText("(")
        logic.commitGestureWord("sí")
        assertEquals("(sí ", text)
        logic.onSpace()
        logic.commitGestureWord("no")
        assertEquals("(sí  no ", text)
    }

    @Test
    fun `punctuation after a gesture word swallows its space`() {
        logic.commitGestureWord("hola")
        logic.onText(",")
        assertEquals("hola,", text)
    }

    @Test
    fun `a gesture after punctuation is separated`() {
        logic.commitGestureWord("hola")
        logic.onText(",")
        logic.commitGestureWord("mundo")
        assertEquals("hola, mundo ", text)
    }

    @Test
    fun `backspace takes the whole gesture word and its spacing back`() {
        "hol".forEach { logic.onText(it.toString()) }
        logic.commitGestureWord("mundo")
        logic.onDelete()
        assertEquals("hol", text)
        // The hand typed word is plain text now; the gesture left nothing to learn.
        assertEquals(listOf("hol"), hook.finished.map { it.first })
        assertEquals("", hook.composing.last())
        logic.onDelete()
        assertEquals("ho", text)
    }

    @Test
    fun `backspace twice removes the word and then letters of what is before`() {
        logic.commitGestureWord("hola")
        logic.commitGestureWord("mundo")
        logic.onDelete()
        assertEquals("hola ", text)
        logic.onDelete()
        // Only the latest gesture word can be taken back as a whole; this is an ordinary backspace.
        assertEquals("hola", text)
    }

    @Test
    fun `shift capitalizes the gesture word`() {
        logic.onShiftTap()
        logic.commitGestureWord("hola", listOf("hora"))
        assertEquals("Hola ", text)
        assertEquals(listOf("Hora"), hook.gestures.single().third)
        assertEquals(ShiftState.OFF, logic.state.value.shift)
    }

    @Test
    fun `caps lock upper cases the whole word`() {
        logic.onShiftTap()
        logic.onShiftTap()
        logic.commitGestureWord("hola")
        assertEquals("HOLA ", text)
    }

    @Test
    fun `an alternative replaces the word and keeps the spacing`() {
        "hol".forEach { logic.onText(it.toString()) }
        logic.commitGestureWord("mundo", listOf("mundial"))
        assertTrue(logic.canReplaceGestureWord)
        assertTrue(logic.replaceGestureWord("mundial", listOf("mundo")))
        assertEquals("hol mundial ", text)
        assertEquals(Triple("mundial", "hol mundial ", listOf("mundo")), hook.gestures.last())
        logic.onDelete()
        assertEquals("hol", text)
    }

    @Test
    fun `learning waits until the word stays`() {
        logic.commitGestureWord("hola")
        assertTrue(hook.finished.isEmpty())
        logic.replaceGestureWord("hora")
        logic.onText("x")
        assertEquals(listOf("hora"), hook.finished.map { it.first })
        assertEquals("", hook.finished.single().second)
    }

    @Test
    fun `a word that is swapped or taken back is not learned`() {
        logic.commitGestureWord("hola")
        logic.onDelete()
        assertTrue(hook.finished.isEmpty())
        logic.commitGestureWord("mundo")
        logic.commitGestureWord("dos")
        assertEquals(listOf("mundo"), hook.finished.map { it.first })
        logic.onFinishInput()
        assertEquals(listOf("mundo", "dos"), hook.finished.map { it.first })
    }

    @Test
    fun `nothing can be swapped once something else was typed`() {
        logic.commitGestureWord("hola")
        logic.onSpace()
        assertFalse(logic.canReplaceGestureWord)
        assertFalse(logic.replaceGestureWord("hora"))
        assertEquals("hola  ", text)
    }

    @Test
    fun `an edit around the word makes the swap and the undo give up`() {
        logic.commitGestureWord("hola")
        editor.commitText("!")
        assertFalse(logic.replaceGestureWord("hora"))
        logic.onDelete()
        assertEquals("hola ", text)
    }

    @Test
    fun `enter and the selection end the undo window`() {
        logic.commitGestureWord("hola")
        logic.onEnter()
        assertFalse(logic.canReplaceGestureWord)
        logic.commitGestureWord("mundo")
        editor.setSelection(0, 4)
        logic.onDelete()
        assertEquals(" mundo ", text)
    }

    @Test
    fun `no editor, no text field or an empty word types nothing`() {
        assertFalse(logic.commitGestureWord(""))
        val number = InputLogic()
        assertFalse(number.commitGestureWord("hola"))
        assertFalse(number.replaceGestureWord("hola"))
        number.onStartInput(
            editor,
            EditorContext.from(InputType.TYPE_CLASS_NUMBER, 0),
            restarting = false,
            initialCursor = 0
        )
        assertFalse(number.commitGestureWord("hola"))
        assertEquals("", text)
        assertEquals("", number.contextText())
    }

    @Test
    fun `the context is the text before the cursor`() {
        logic.commitGestureWord("hola")
        assertEquals("hola ", logic.contextText())
    }

    @Test
    fun `symbols pages do not change the case`() {
        logic.onShiftTap()
        logic.showPage(Page.SYMBOLS_1)
        logic.commitGestureWord("hola")
        assertEquals("hola ", text)
    }
}
