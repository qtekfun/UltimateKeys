// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.logic

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputLogicTest {
    private var now = 1_000L
    private val sentences = EditorContext.from(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        EditorInfo.IME_ACTION_DONE
    )
    private val plain = EditorContext.from(InputType.TYPE_CLASS_TEXT, 0)

    private fun logic(
        editor: FakeEditorConnection = FakeEditorConnection(),
        context: EditorContext = plain,
        options: InputOptions = InputOptions()
    ): Pair<InputLogic, FakeEditorConnection> {
        val logic = InputLogic { now }
        logic.options = options
        logic.onStartInput(editor, context, restarting = false, initialCursor = editor.cursor)
        return logic to editor
    }

    private fun InputLogic.type(s: String) = s.forEach { onText(it.toString()) }

    @Test
    fun `types plain text`() {
        val (l, e) = logic()
        l.type("hola")
        assertEquals("hola", e.text.toString())
        assertEquals(0, e.batchDepth)
    }

    @Test
    fun `shift once capitalizes one letter then turns off`() {
        val (l, e) = logic()
        l.onShiftTap()
        l.type("ab")
        assertEquals("Ab", e.text.toString())
        assertEquals(ShiftState.OFF, l.state.value.shift)
    }

    @Test
    fun `double tap on shift locks caps and a tap unlocks`() {
        val (l, e) = logic()
        l.onShiftTap()
        now += 100
        l.onShiftTap()
        assertEquals(ShiftState.LOCKED, l.state.value.shift)
        l.type("ab")
        assertEquals("AB", e.text.toString())
        now += 1_000
        l.onShiftTap()
        assertEquals(ShiftState.OFF, l.state.value.shift)
    }

    @Test
    fun `slow second tap on shift turns it off instead of locking`() {
        val (l, _) = logic()
        l.onShiftTap()
        now += 1_000
        l.onShiftTap()
        assertEquals(ShiftState.OFF, l.state.value.shift)
    }

    @Test
    fun `shift does not change symbols`() {
        val (l, e) = logic()
        l.showPage(Page.SYMBOLS_1)
        l.onShiftTap()
        l.onText("a")
        assertEquals("a", e.text.toString())
    }

    @Test
    fun `uses the layout locale for upper casing`() {
        val (l, e) = logic()
        l.locale = java.util.Locale.forLanguageTag("tr")
        l.onShiftTap()
        l.onText("i")
        assertEquals("İ", e.text.toString())
    }

    @Test
    fun `auto capitalizes at the start and after a sentence end`() {
        val (l, e) = logic(context = sentences)
        assertEquals(ShiftState.ONCE, l.state.value.shift)
        l.type("hi")
        assertEquals("Hi", e.text.toString())
        assertEquals(ShiftState.OFF, l.state.value.shift)
        l.onText(".")
        l.onSpace()
        assertEquals(ShiftState.ONCE, l.state.value.shift)
    }

    @Test
    fun `auto capitalization respects the setting and field kind`() {
        val (off, _) = logic(context = sentences, options = InputOptions(autoCapitalize = false))
        assertEquals(ShiftState.OFF, off.state.value.shift)
        val password = EditorContext.from(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            0
        )
        val (p, _) = logic(context = password)
        assertEquals(ShiftState.OFF, p.state.value.shift)
    }

    @Test
    fun `auto shift does not override a locked caps`() {
        val (l, _) = logic(context = sentences)
        l.onShiftTap()
        now += 100
        l.onShiftTap()
        assertEquals(ShiftState.LOCKED, l.state.value.shift)
        l.onSelectionChanged(0, 0, -1, -1)
        assertEquals(ShiftState.LOCKED, l.state.value.shift)
    }

    @Test
    fun `auto shift clears itself when the cursor moves mid sentence`() {
        val e = FakeEditorConnection("Hello world")
        val (l, _) = logic(editor = e, context = sentences)
        e.setSelection(0, 0)
        l.onSelectionChanged(0, 0, -1, -1)
        assertEquals(ShiftState.ONCE, l.state.value.shift)
        e.setSelection(5, 5)
        l.onSelectionChanged(5, 5, -1, -1)
        assertEquals(ShiftState.OFF, l.state.value.shift)
    }

    @Test
    fun `double space inserts a period`() {
        val (l, e) = logic()
        l.type("hola")
        l.onSpace()
        now += 200
        l.onSpace()
        assertEquals("hola. ", e.text.toString())
    }

    @Test
    fun `double space needs speed, a word before it and the setting`() {
        val (slow, e1) = logic()
        slow.type("hola")
        slow.onSpace()
        now += 5_000
        slow.onSpace()
        assertEquals("hola  ", e1.text.toString())

        val (afterSpace, e2) = logic(editor = FakeEditorConnection(" "))
        afterSpace.onSpace()
        now += 100
        afterSpace.onSpace()
        assertEquals("   ", e2.text.toString())

        val (off, e3) = logic(options = InputOptions(doubleSpacePeriod = false))
        off.type("hola")
        off.onSpace()
        now += 100
        off.onSpace()
        assertEquals("hola  ", e3.text.toString())
    }

    @Test
    fun `punctuation swallows an automatic space only`() {
        val (l, e) = logic()
        l.commitWithAutoSpace("hola")
        l.onText(",")
        assertEquals("hola,", e.text.toString())

        val (manual, e2) = logic()
        manual.type("hola")
        manual.onSpace()
        manual.onText(",")
        assertEquals("hola ,", e2.text.toString())

        val (off, e3) = logic(options = InputOptions(smartPunctuation = false))
        off.commitWithAutoSpace("hola")
        off.onText(",")
        assertEquals("hola ,", e3.text.toString())
    }

    @Test
    fun `enter runs the editor action when there is one`() {
        val (l, e) = logic(context = sentences)
        l.onEnter()
        assertEquals(listOf(EditorInfo.IME_ACTION_DONE), e.actions)
        assertEquals(EnterKind.DONE, l.state.value.enterKind)
        assertEquals("", e.text.toString())
    }

    @Test
    fun `enter inserts a line break when the field says no enter action`() {
        val multi = EditorContext.from(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        )
        val (l, e) = logic(context = multi)
        l.type("a")
        l.onEnter()
        assertEquals("a\n", e.text.toString())
        assertTrue(e.actions.isEmpty())
        assertEquals(EnterKind.ENTER, l.state.value.enterKind)
    }

    @Test
    fun `enter sends a key event in single line fields without an action`() {
        val (l, e) = logic()
        l.onEnter()
        assertEquals(1, e.enterKeys)
    }

    @Test
    fun `delete removes a character, a surrogate pair or the selection`() {
        val (l, e) = logic(editor = FakeEditorConnection("ab😀"))
        l.onDelete()
        assertEquals("ab", e.text.toString())
        e.setSelection(0, 2)
        l.onDelete()
        assertEquals("", e.text.toString())
    }

    @Test
    fun `delete word removes trailing spaces and the word`() {
        val (l, e) = logic(editor = FakeEditorConnection("uno dos  "))
        l.onDeleteWord()
        assertEquals("uno ", e.text.toString())
        l.onDeleteWord()
        assertEquals("", e.text.toString())
        l.onDeleteWord()
        assertEquals("", e.text.toString())
    }

    @Test
    fun `composing keeps words as composing text and backspace edits it`() {
        val (l, e) = logic(options = InputOptions(composeWords = true))
        l.type("hol")
        assertEquals("hol", e.composingText)
        l.onDelete()
        assertEquals("ho", e.composingText)
        l.onDelete()
        l.onDelete()
        assertEquals("", e.text.toString())
        assertEquals("", l.composingText)
    }

    @Test
    fun `composing ends at a separator and when the cursor leaves the word`() {
        val (l, e) = logic(options = InputOptions(composeWords = true))
        l.type("hola")
        l.onText(",")
        assertEquals("hola,", e.text.toString())
        assertEquals("", e.composingText)

        l.type("ab")
        assertEquals("ab", l.composingText)
        l.onSelectionChanged(0, 0, 5, 7)
        assertEquals("", l.composingText)
    }

    @Test
    fun `composing is off for passwords and numeric fields`() {
        val password = EditorContext.from(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            0
        )
        val (l, e) = logic(context = password, options = InputOptions(composeWords = true))
        l.type("abc")
        assertEquals("", e.composingText)
        assertEquals("abc", e.text.toString())
    }

    @Test
    fun `moves the cursor and clamps at the ends`() {
        val e = FakeEditorConnection("abcde")
        val (l, _) = logic(editor = e)
        l.moveCursor(-2)
        assertEquals(3, e.cursor)
        l.moveCursor(-10)
        assertEquals(0, e.cursor)
        l.moveCursor(10)
        assertEquals(5, e.cursor)
        l.moveCursor(0)
        assertEquals(5, e.cursor)
    }

    @Test
    fun `delete drag selects, shrinks and deletes`() {
        val e = FakeEditorConnection("hola mundo")
        val (l, _) = logic(editor = e)
        val anchor = 10
        assertEquals(5, l.extendSelectionLeft(5, anchor))
        assertEquals("mundo", e.selectedText().toString())
        l.shrinkSelection(2, anchor)
        assertEquals("ndo", e.selectedText().toString())
        l.deleteSelection()
        assertEquals("hola mu", e.text.toString())
    }

    @Test
    fun `delete drag stops at the start of the text and by words`() {
        val e = FakeEditorConnection("hola mundo")
        val (l, _) = logic(editor = e)
        assertEquals(5, l.wordLengthBeforeCursor())
        assertEquals(5, l.extendSelectionLeft(l.wordLengthBeforeCursor(), 10))
        assertEquals(5, l.extendSelectionLeft(50, 10))
        assertEquals(0, l.extendSelectionLeft(1, 10))
        l.shrinkSelection(100, 10)
        assertEquals("", e.selectedText().toString())
    }

    @Test
    fun `field kinds choose the page`() {
        val number = EditorContext.from(InputType.TYPE_CLASS_NUMBER, 0)
        val phone = EditorContext.from(InputType.TYPE_CLASS_PHONE, 0)
        assertEquals(Page.NUMERIC, logic(context = number).first.state.value.page)
        assertEquals(Page.PHONE, logic(context = phone).first.state.value.page)
        assertEquals(Page.LETTERS, logic().first.state.value.page)
    }

    @Test
    fun `symbols page survives a restart but not a new field`() {
        val (l, e) = logic()
        l.showPage(Page.SYMBOLS_1)
        l.onStartInput(e, plain, restarting = true)
        assertEquals(Page.SYMBOLS_1, l.state.value.page)
        l.onStartInput(e, plain, restarting = false)
        assertEquals(Page.LETTERS, l.state.value.page)
    }

    @Test
    fun `editor context classifies input types`() {
        fun kind(t: Int) = EditorContext.from(t, 0).kind
        assertEquals(
            InputKind.EMAIL,
            kind(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        )
        assertEquals(
            InputKind.URL,
            kind(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        )
        assertEquals(
            InputKind.PASSWORD,
            kind(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        )
        assertEquals(
            InputKind.PASSWORD,
            kind(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        )
        assertEquals(InputKind.NUMBER, kind(InputType.TYPE_CLASS_DATETIME))
        assertEquals(InputKind.NONE, kind(InputType.TYPE_NULL))
        assertFalse(
            EditorContext.from(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_GO).noEnterAction
        )
        assertEquals(
            EnterKind.SEARCH,
            EditorContext.from(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH).enterKind
        )
        assertEquals(
            EnterKind.NEXT,
            EditorContext.from(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_NEXT).enterKind
        )
        assertEquals(
            EnterKind.PREVIOUS,
            EditorContext.from(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_PREVIOUS).enterKind
        )
        assertEquals(
            EnterKind.SEND,
            EditorContext.from(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEND).enterKind
        )
    }

    @Test
    fun `events after the input finished are ignored`() {
        val (l, e) = logic()
        l.onFinishInput()
        l.onText("a")
        l.onSpace()
        l.onDelete()
        l.onEnter()
        assertEquals("", e.text.toString())
    }
}

class AutoCorrectTest {
    private class Hook(var correction: String? = null) : SuggestionHook {
        val composings = mutableListOf<String>()
        val finished = mutableListOf<Triple<String, String, Boolean>>()
        val rejected = mutableListOf<String>()

        override fun onComposingChanged(composing: String, contextBefore: String) {
            composings += composing
        }

        override fun autoCorrectFor(composing: String) = correction.takeIf { composing == "teh" }

        override fun onWordFinished(word: String, contextBefore: String, corrected: Boolean) {
            finished += Triple(word, contextBefore, corrected)
        }

        override fun onAutoCorrectRejected(original: String) {
            rejected += original
        }
    }

    private fun rig(
        correction: String? = "the",
        options: InputOptions = InputOptions(composeWords = true)
    ): Triple<InputLogic, FakeEditorConnection, Hook> {
        val e = FakeEditorConnection()
        val hook = Hook(correction)
        val logic = InputLogic { 1_000L }
        logic.options = options
        logic.suggestionHook = hook
        logic.onStartInput(
            e,
            EditorContext.from(InputType.TYPE_CLASS_TEXT, 0),
            restarting = false,
            initialCursor = 0
        )
        return Triple(logic, e, hook)
    }

    private fun InputLogic.type(s: String) = s.forEach { onText(it.toString()) }

    @Test
    fun `space applies the autocorrection and backspace undoes it`() {
        val (l, e, hook) = rig()
        l.type("teh")
        assertEquals(listOf("t", "te", "teh"), hook.composings)
        l.onSpace()
        assertEquals("the ", e.text.toString())
        assertEquals(Triple("the", "", true), hook.finished.last())
        l.onDelete()
        assertEquals("teh", e.text.toString())
        assertEquals("teh", l.composingText)
        assertEquals(listOf("teh"), hook.rejected)
        // The rejected word is not corrected again.
        l.onSpace()
        assertEquals("teh ", e.text.toString())
    }

    @Test
    fun `punctuation also corrects and is kept`() {
        val (l, e, _) = rig()
        l.type("teh")
        l.onText(",")
        assertEquals("the,", e.text.toString())
        l.onDelete()
        assertEquals("teh", e.text.toString())
    }

    @Test
    fun `no correction when off or not offered`() {
        val (off, e1, _) = rig(options = InputOptions(composeWords = true, autoCorrect = false))
        off.type("teh")
        off.onSpace()
        assertEquals("teh ", e1.text.toString())
        val (none, e2, hook) = rig(correction = null)
        none.type("teh")
        none.onEnter()
        assertEquals("teh", e2.text.toString())
        assertEquals(Triple("teh", "", false), hook.finished.last())
    }

    @Test
    fun `undo only works right after the correction`() {
        val (l, e, _) = rig()
        l.type("teh")
        l.onSpace()
        l.type("x")
        l.onDelete()
        l.onDelete()
        assertEquals("the", e.text.toString())
    }

    @Test
    fun `picking a suggestion replaces the composing word and adds a space`() {
        val (l, e, hook) = rig()
        l.type("hol")
        l.commitWithAutoSpace("hola")
        assertEquals("hola ", e.text.toString())
        assertEquals("", l.composingText)
        assertEquals("hola", hook.finished.last().first)
        l.onText(",")
        assertEquals("hola,", e.text.toString())
    }

    @Test
    fun `cursor moves and deletions tell the hook that composing ended`() {
        val (l, _, hook) = rig()
        l.type("ab")
        l.moveCursor(-1)
        assertEquals("", hook.composings.last())
        l.type("c")
        l.onSelectionChanged(0, 0, -1, -1)
        assertEquals("", hook.composings.last())
    }
}
