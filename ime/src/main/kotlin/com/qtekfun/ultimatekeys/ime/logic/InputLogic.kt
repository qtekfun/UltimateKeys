// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.logic

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Behavior switches, mirrored from the user settings. */
data class InputOptions(
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val smartPunctuation: Boolean = true,
    /** Phase 2 turns this on: letters are then held as composing text. */
    val composeWords: Boolean = false
)

/**
 * All typing behavior: what each key does to the editor. Pure Kotlin over [EditorConnection], so it
 * can be tested without Android. Not thread-safe: call it from the main thread only.
 */
@Suppress("TooManyFunctions")
class InputLogic(private val clock: () -> Long = System::currentTimeMillis) {
    private val mutableState = MutableStateFlow(KeyboardState())
    val state: StateFlow<KeyboardState> = mutableState.asStateFlow()

    var options = InputOptions()
    var locale: Locale = Locale.forLanguageTag("es")

    private var editor: EditorConnection? = null
    private var context = EditorContext()
    private val composing = StringBuilder()
    private var lastShiftTapAt = NEVER
    private var lastSpaceAt = NEVER
    private var autoSpace = false
    private var shiftIsAutomatic = false

    /** Cursor position as last reported by the editor or moved by us. -1 when unknown. */
    var cursor = -1
        private set

    val composingText: String get() = composing.toString()

    fun onStartInput(
        editor: EditorConnection,
        context: EditorContext,
        restarting: Boolean,
        initialCursor: Int = -1
    ) {
        cursor = initialCursor
        this.editor = editor
        this.context = context
        composing.clear()
        lastSpaceAt = NEVER
        autoSpace = false
        val page = when (context.kind) {
            InputKind.NUMBER -> Page.NUMERIC
            InputKind.PHONE -> Page.PHONE
            else -> keepSymbolsOnRestart(restarting)
        }
        mutableState.value = mutableState.value.copy(
            page = page,
            enterKind = context.enterKind.takeIf { context.enterRunsAction } ?: EnterKind.ENTER,
            shift = if (restarting) mutableState.value.shift else ShiftState.OFF
        )
        refreshAutoShift()
    }

    fun onFinishInput() {
        editor?.finishComposingText()
        composing.clear()
        editor = null
    }

    fun onSelectionChanged(newStart: Int, newEnd: Int, composingStart: Int, composingEnd: Int) {
        cursor = newStart
        val stillInWord = composingStart >= 0 && newStart == composingEnd && newEnd == composingEnd
        if (composing.isNotEmpty() && !stillInWord) {
            // The cursor moved away from the word being composed (tap in the text, app edit).
            composing.clear()
        }
        refreshAutoShift()
    }

    // region Typing

    /** Types text produced by a character key (shift is applied here). */
    fun onText(text: String) {
        val ed = editor ?: return
        val output = applyShift(text)
        ed.beginBatchEdit()
        try {
            if (!tryCompose(ed, output)) {
                finishComposing(ed)
                smartPunctuationFix(ed, output)
                ed.commitText(output)
                autoSpace = false
            }
            lastSpaceAt = NEVER
        } finally {
            ed.endBatchEdit()
        }
        afterTyping()
    }

    /** Commits [word] followed by a space that punctuation may swallow (used by suggestions). */
    fun commitWithAutoSpace(word: String) {
        val ed = editor ?: return
        ed.beginBatchEdit()
        try {
            composing.clear()
            ed.commitText("$word ")
            autoSpace = true
            lastSpaceAt = NEVER
        } finally {
            ed.endBatchEdit()
        }
        afterTyping()
    }

    fun onSpace() {
        val ed = editor ?: return
        val now = clock()
        ed.beginBatchEdit()
        try {
            finishComposing(ed)
            if (shouldInsertDoubleSpacePeriod(ed, now)) {
                ed.deleteSurroundingText(1, 0)
                ed.commitText(". ")
                lastSpaceAt = NEVER
            } else {
                ed.commitText(" ")
                lastSpaceAt = now
            }
            autoSpace = false
        } finally {
            ed.endBatchEdit()
        }
        afterTyping()
    }

    fun onEnter() {
        val ed = editor ?: return
        ed.beginBatchEdit()
        try {
            finishComposing(ed)
            when {
                context.enterRunsAction -> ed.performEditorAction(context.actionId)
                context.multiLine -> ed.commitText("\n")
                else -> ed.sendEnterKey()
            }
            lastSpaceAt = NEVER
            autoSpace = false
        } finally {
            ed.endBatchEdit()
        }
        afterTyping()
    }

    /** One backspace: removes the selection, the last composing char or the previous character. */
    fun onDelete() {
        val ed = editor ?: return
        ed.beginBatchEdit()
        try {
            when {
                ed.selectedText().isNotEmpty() -> ed.commitText("")

                composing.isNotEmpty() -> deleteFromComposing(ed)

                else -> {
                    val before = ed.textBeforeCursor(2)
                    val count = if (before.length == 2 &&
                        Character.isSurrogatePair(before[0], before[1])
                    ) {
                        2
                    } else {
                        1
                    }
                    ed.deleteSurroundingText(count, 0)
                }
            }
            lastSpaceAt = NEVER
            autoSpace = false
        } finally {
            ed.endBatchEdit()
        }
        afterTyping()
    }

    /** Deletes the word before the cursor (and the spaces right before it). */
    fun onDeleteWord() {
        val ed = editor ?: return
        finishComposing(ed)
        val before = ed.textBeforeCursor(WORD_LOOKBEHIND).toString()
        val count = wordLengthBefore(before)
        if (count > 0) ed.deleteSurroundingText(count, 0)
        lastSpaceAt = NEVER
        afterTyping()
    }

    // endregion

    // region Shift and pages

    fun onShiftTap() {
        val now = clock()
        val current = mutableState.value.shift
        val isDoubleTap = lastShiftTapAt != NEVER && now - lastShiftTapAt <= DOUBLE_TAP_MS
        val next = when {
            current == ShiftState.LOCKED -> ShiftState.OFF
            isDoubleTap -> ShiftState.LOCKED
            current == ShiftState.OFF -> ShiftState.ONCE
            else -> ShiftState.OFF
        }
        lastShiftTapAt = now
        shiftIsAutomatic = false
        mutableState.value = mutableState.value.copy(shift = next)
    }

    fun showPage(page: Page) {
        mutableState.value = mutableState.value.copy(page = page)
    }

    // endregion

    // region Cursor movement (spacebar drag)

    /** Moves the cursor by [delta] characters, clamped to the text. */
    fun moveCursor(delta: Int) {
        val ed = editor ?: return
        if (delta == 0) return
        finishComposing(ed)
        val target = if (delta < 0) {
            val available = ed.textBeforeCursor(-delta).length
            if (cursor >= 0) cursor - available else return
        } else {
            val available = ed.textAfterCursor(delta).length
            if (cursor >= 0) cursor + available else return
        }
        ed.setSelection(target, target)
        cursor = target
    }

    /**
     * Grows the selection leftwards by up to [chars] characters while keeping its end at [anchor]
     * (delete-key drag). Returns how many characters were added.
     */
    fun extendSelectionLeft(chars: Int, anchor: Int): Int {
        val ed = editor ?: return 0
        if (cursor < 0) return 0
        finishComposing(ed)
        val available = ed.textBeforeCursor(chars).length
        if (available == 0) return 0
        val start = cursor - available
        ed.setSelection(start, anchor)
        cursor = start
        return available
    }

    /** Shrinks the selection from the left by up to [chars] characters, never past [anchor]. */
    fun shrinkSelection(chars: Int, anchor: Int) {
        val ed = editor ?: return
        if (cursor < 0) return
        val start = minOf(anchor, cursor + chars)
        ed.setSelection(start, anchor)
        cursor = start
    }

    fun wordLengthBeforeCursor(): Int {
        val ed = editor ?: return 0
        return wordLengthBefore(ed.textBeforeCursor(WORD_LOOKBEHIND).toString())
    }

    fun deleteSelection() {
        val ed = editor ?: return
        if (ed.selectedText().isNotEmpty()) ed.commitText("")
        afterTyping()
    }

    // endregion

    private fun tryCompose(ed: EditorConnection, text: String): Boolean {
        if (!options.composeWords || !context.isTextual) return false
        val isWordChar =
            text.length == 1 && (text[0].isLetter() || (text[0] == '\'' && composing.isNotEmpty()))
        if (!isWordChar) return false
        composing.append(text)
        ed.setComposingText(composing)
        return true
    }

    private fun finishComposing(ed: EditorConnection) {
        if (composing.isNotEmpty()) {
            ed.finishComposingText()
            composing.clear()
        }
    }

    private fun deleteFromComposing(ed: EditorConnection) {
        composing.deleteCharAt(composing.length - 1)
        if (composing.isEmpty()) {
            ed.commitText("")
        } else {
            ed.setComposingText(composing)
        }
    }

    /** Removes the auto-inserted space before closing punctuation. */
    private fun smartPunctuationFix(ed: EditorConnection, text: String) {
        val swallows =
            options.smartPunctuation && autoSpace && text.length == 1 && text[0] in SPACE_SWALLOWING
        if (swallows && ed.textBeforeCursor(1).toString() == " ") ed.deleteSurroundingText(1, 0)
    }

    private fun shouldInsertDoubleSpacePeriod(ed: EditorConnection, now: Long): Boolean {
        if (!options.doubleSpacePeriod || !context.isTextual) return false
        if (lastSpaceAt == NEVER || now - lastSpaceAt > DOUBLE_SPACE_MS) return false
        val before = ed.textBeforeCursor(2).toString()
        return before.length == 2 && before[1] == ' ' &&
            (before[0].isLetterOrDigit() || before[0] in CLOSERS)
    }

    private fun applyShift(text: String): String {
        val page = mutableState.value.page
        return if (page == Page.LETTERS && mutableState.value.shift != ShiftState.OFF) {
            text.uppercase(locale)
        } else {
            text
        }
    }

    private fun afterTyping() {
        if (mutableState.value.shift == ShiftState.ONCE) {
            mutableState.value = mutableState.value.copy(shift = ShiftState.OFF)
        }
        refreshAutoShift()
    }

    private fun refreshAutoShift() {
        val ed = editor ?: return
        val state = mutableState.value
        if (!options.autoCapitalize || !context.isTextual || state.page != Page.LETTERS) return
        val wantsCaps = ed.cursorCapsMode(context.inputType) != 0
        when {
            state.shift == ShiftState.OFF && wantsCaps -> {
                shiftIsAutomatic = true
                mutableState.value = state.copy(shift = ShiftState.ONCE)
            }

            state.shift == ShiftState.ONCE && shiftIsAutomatic && !wantsCaps ->
                mutableState.value = state.copy(shift = ShiftState.OFF)
        }
    }

    private fun keepSymbolsOnRestart(restarting: Boolean): Page {
        val current = mutableState.value.page
        return if (restarting &&
            (current == Page.SYMBOLS_1 || current == Page.SYMBOLS_2)
        ) {
            current
        } else {
            Page.LETTERS
        }
    }

    private fun wordLengthBefore(text: String): Int {
        var i = text.length
        while (i > 0 && text[i - 1].isWhitespace()) i--
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        return text.length - i
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
        const val DOUBLE_TAP_MS = 400L
        const val DOUBLE_SPACE_MS = 600L
        const val WORD_LOOKBEHIND = 64
        const val SPACE_SWALLOWING = ".,;:!?)"
        const val CLOSERS = ")]\"'"
    }
}
