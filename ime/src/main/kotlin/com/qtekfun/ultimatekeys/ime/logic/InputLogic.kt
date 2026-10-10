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
    val composeWords: Boolean = false,
    val autoCorrect: Boolean = true
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
    private var undo: AutoCorrectUndo? = null
    private var rejectedCorrection: String? = null
    private var gestureUndo: GestureUndo? = null

    var suggestionHook: SuggestionHook? = null

    /** Cursor position as last reported by the editor or moved by us. -1 when unknown. */
    var cursor = -1
        private set

    val composingText: String get() = composing.toString()

    private data class AutoCorrectUndo(
        val original: String,
        val corrected: String,
        val separator: String
    )

    /**
     * A word typed by gesture, as inserted: [lead] (a space when it had to be separated from the word
     * before), the word and the space after it. Kept until the next edit so that backspace can take
     * it back and the strip can swap it for an alternative; its learning waits until then too.
     */
    private data class GestureUndo(val lead: String, val word: String, val contextBefore: String) {
        val inserted: String get() = "$lead$word "
    }

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
        gestureUndo = null
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
        settleGesture()
        editor?.finishComposingText()
        composing.clear()
        editor = null
    }

    fun onSelectionChanged(newStart: Int, newEnd: Int, composingStart: Int, composingEnd: Int) {
        cursor = newStart
        val stillInWord = composingStart >= 0 && newStart == composingEnd && newEnd == composingEnd
        if (composing.isNotEmpty() && !stillInWord) {
            // The cursor moved away from the word being composed (tap in the text, app edit). The
            // editor still holds it as composing text: settle it, or the next letter replaces it.
            composing.clear()
            editor?.finishComposingText()
            suggestionHook?.onComposingChanged("", "")
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
            clearUndo()
            if (!tryCompose(ed, output) && !finishWord(ed, output)) {
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

    /**
     * Inserts [text] exactly as given (an emoji or a clipboard entry): no shift, no smart
     * punctuation, and the word being typed is finished first without being autocorrected.
     */
    fun insertVerbatim(text: String) {
        val ed = editor ?: return
        ed.beginBatchEdit()
        try {
            undo = null
            finishWord(ed, null)
            ed.commitText(text)
            autoSpace = false
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
            clearUndo()
            if (composing.isNotEmpty()) {
                composing.clear()
                ed.setComposingText(word)
                ed.finishComposingText()
                ed.commitText(" ")
            } else {
                ed.commitText("$word ")
            }
            autoSpace = true
            lastSpaceAt = NEVER
            suggestionHook?.onWordFinished(word, contextBefore(ed), false)
            suggestionHook?.onComposingChanged("", contextBefore(ed))
        } finally {
            ed.endBatchEdit()
        }
        afterTyping()
    }

    /**
     * Inserts dictated [text] at the cursor as one edit, so a single undo removes all of it. Spacing
     * and capitalization follow what is around the cursor (see [DictationFormatter]). The words feed
     * learning only when [learn] is true (never while private mode is on).
     */
    fun insertDictation(text: String, language: String, learn: Boolean) {
        val ed = editor ?: return
        ed.beginBatchEdit()
        try {
            undo = null
            finishComposing(ed)
            val before = ed.textBeforeCursor(CONTEXT_CHARS)
            val formatted = DictationFormatter.format(
                text,
                before,
                ed.textAfterCursor(AFTER_CHARS),
                language,
                prose = context.isTextual
            )
            if (formatted.isEmpty()) return
            ed.commitText(formatted)
            autoSpace = false
            lastSpaceAt = NEVER
            if (learn) learnWords(formatted, before.toString())
            suggestionHook?.onComposingChanged("", contextBefore(ed))
        } finally {
            ed.endBatchEdit()
        }
        afterTyping()
    }

    private fun learnWords(inserted: String, before: String) {
        val hook = suggestionHook ?: return
        var history = before
        inserted.split(' ', '\n').forEach { token ->
            val word = token.trim { !it.isLetter() }
            if (word.length >= MIN_LEARNED_WORD && word.all { it.isLetter() || it == '\'' }) {
                hook.onWordFinished(word, history, false)
            }
            history = "$history$token "
        }
    }

    fun onSpace() {
        val ed = editor ?: return
        val now = clock()
        ed.beginBatchEdit()
        try {
            clearUndo()
            if (finishWord(ed, " ")) {
                lastSpaceAt = now
            } else if (shouldInsertDoubleSpacePeriod(ed, now)) {
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
            clearUndo()
            finishWord(ed, null)
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
            if (undoGesture(ed) || undoAutoCorrect(ed)) {
                afterTyping()
                return
            }
            clearUndo()
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

    // region Gesture typing

    /** The text before the cursor, as the suggestion side wants it for context. */
    fun contextText(): String = editor?.let { contextBefore(it) }.orEmpty()

    /**
     * Types a word the person glided over the letters: a space first when it would otherwise stick to the
     * word before, the word (in the case shift asks for) and a space after it that punctuation may
     * swallow. [alternatives] are the other candidates; the strip offers them (see [replaceGestureWord]).
     * Returns false when nothing was typed (no editor, or a field that is not for text).
     */
    fun commitGestureWord(word: String, alternatives: List<String> = emptyList()): Boolean {
        val ed = editor ?: return false
        if (!context.isTextual || word.isEmpty()) return false
        ed.beginBatchEdit()
        try {
            clearUndo()
            finishWord(ed, null)
            val before = contextBefore(ed)
            val shown = applyGestureCase(word)
            val lead = if (needsSpaceBefore(ed.textBeforeCursor(1))) " " else ""
            ed.commitText("$lead$shown ")
            gestureUndo = GestureUndo(lead, shown, before)
            autoSpace = true
            lastSpaceAt = NEVER
            suggestionHook?.onGestureCommitted(
                shown,
                contextBefore(ed),
                alternatives.map(::applyGestureCase)
            )
        } finally {
            ed.endBatchEdit()
        }
        afterTyping()
        return true
    }

    /** True while the last thing typed is a gesture word that [replaceGestureWord] can still swap. */
    val canReplaceGestureWord: Boolean get() = gestureUndo != null

    /** Swaps the word just typed by gesture for [word] (already in the wanted case), keeping its spacing. */
    fun replaceGestureWord(word: String, alternatives: List<String> = emptyList()): Boolean {
        val ed = editor ?: return false
        val pending = gestureUndo ?: return false
        if (word.isEmpty() || !gestureStillBeforeCursor(ed, pending)) return false
        ed.beginBatchEdit()
        try {
            ed.deleteSurroundingText(pending.inserted.length, 0)
            ed.commitText("${pending.lead}$word ")
            gestureUndo = pending.copy(word = word)
            autoSpace = true
            suggestionHook?.onGestureCommitted(word, contextBefore(ed), alternatives)
        } finally {
            ed.endBatchEdit()
        }
        return true
    }

    /** Backspace right after a gesture word takes the whole word (and its spacing) back. */
    private fun undoGesture(ed: EditorConnection): Boolean {
        val pending = gestureUndo ?: return false
        if (composing.isNotEmpty() || ed.selectedText().isNotEmpty() ||
            !gestureStillBeforeCursor(ed, pending)
        ) {
            settleGesture()
            return false
        }
        gestureUndo = null
        ed.deleteSurroundingText(pending.inserted.length, 0)
        autoSpace = false
        lastSpaceAt = NEVER
        suggestionHook?.onComposingChanged("", contextBefore(ed))
        return true
    }

    private fun gestureStillBeforeCursor(ed: EditorConnection, pending: GestureUndo): Boolean =
        ed.textBeforeCursor(pending.inserted.length).toString() == pending.inserted

    /** The gesture word stays: from now on it counts as typed, so the engine may learn it. */
    private fun settleGesture() {
        val pending = gestureUndo ?: return
        gestureUndo = null
        suggestionHook?.onWordFinished(pending.word, pending.contextBefore, false)
    }

    private fun clearUndo() {
        undo = null
        settleGesture()
    }

    private fun needsSpaceBefore(previous: CharSequence): Boolean {
        val c = previous.lastOrNull() ?: return false
        return c.isLetterOrDigit() || c in SPACE_SWALLOWING || c in CLOSERS
    }

    private fun applyGestureCase(word: String): String {
        if (mutableState.value.page != Page.LETTERS) return word
        return when (mutableState.value.shift) {
            ShiftState.OFF -> word
            ShiftState.LOCKED -> word.uppercase(locale)
            ShiftState.ONCE -> word.replaceFirstChar { it.uppercase(locale) }
        }
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
        if (composing.isEmpty() && !startComposing(ed)) return false
        composing.append(text)
        ed.setComposingText(composing)
        suggestionHook?.onComposingChanged(composing.toString(), contextBefore(ed))
        return true
    }

    /**
     * Prepares to compose a new word with the letter being typed. When the cursor is at the
     * end of an existing word, that word is taken back as composing text so that it is corrected and
     * suggested as a whole, not as the lone letter. Returns false when the letter must be inserted
     * as plain text: a selection to replace, or the cursor in the middle of a word.
     */
    private fun startComposing(ed: EditorConnection): Boolean {
        if (ed.selectedText().isNotEmpty()) return false
        if (ed.textAfterCursor(1).firstOrNull()?.isLetter() == true) return false
        val before = ed.textBeforeCursor(WORD_LOOKBEHIND).toString()
        val fragment = before.takeLastWhile { it.isLetter() }
        if (fragment.isNotEmpty()) {
            ed.deleteSurroundingText(fragment.length, 0)
            composing.append(fragment)
        }
        return true
    }

    /**
     * Ends the composing word with [separator] (null for none), applying the autocorrection.
     * Returns true when [separator] was committed here.
     */
    private fun finishWord(ed: EditorConnection, separator: String?): Boolean {
        if (composing.isEmpty()) return false
        val original = composing.toString()
        val before = contextBefore(ed)
        val candidate = suggestionHook?.autoCorrectFor(original)
        val corrected = candidate?.takeIf {
            options.autoCorrect && separator != null && it != original &&
                original != rejectedCorrection
        }
        val word = corrected ?: original
        composing.clear()
        rejectedCorrection = null
        ed.setComposingText(word)
        ed.finishComposingText()
        if (separator != null) ed.commitText(separator)
        undo =
            if (corrected != null &&
                separator != null
            ) {
                AutoCorrectUndo(original, corrected, separator)
            } else {
                null
            }
        suggestionHook?.onWordFinished(word, before, corrected != null)
        suggestionHook?.onComposingChanged("", contextBefore(ed))
        return separator != null
    }

    /** Backspace right after an autocorrection restores what the user typed. */
    private fun undoAutoCorrect(ed: EditorConnection): Boolean {
        val pending = undo ?: return false
        undo = null
        val replaced = pending.corrected + pending.separator
        if (composing.isNotEmpty() || ed.selectedText().isNotEmpty()) return false
        if (ed.textBeforeCursor(replaced.length).toString() != replaced) return false
        ed.deleteSurroundingText(replaced.length, 0)
        ed.setComposingText(pending.original)
        composing.append(pending.original)
        rejectedCorrection = pending.original
        suggestionHook?.onAutoCorrectRejected(pending.original)
        suggestionHook?.onComposingChanged(pending.original, contextBefore(ed))
        return true
    }

    private fun contextBefore(ed: EditorConnection): String {
        val before = ed.textBeforeCursor(CONTEXT_CHARS).toString()
        return before.dropLast(composing.length.coerceAtMost(before.length))
    }

    private fun finishComposing(ed: EditorConnection) {
        if (composing.isNotEmpty()) {
            ed.finishComposingText()
            composing.clear()
            suggestionHook?.onComposingChanged("", contextBefore(ed))
        }
    }

    private fun deleteFromComposing(ed: EditorConnection) {
        composing.deleteCharAt(composing.length - 1)
        if (composing.isEmpty()) {
            ed.commitText("")
        } else {
            ed.setComposingText(composing)
        }
        suggestionHook?.onComposingChanged(composing.toString(), contextBefore(ed))
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
        const val CONTEXT_CHARS = 120
        const val AFTER_CHARS = 8
        const val MIN_LEARNED_WORD = 2
        const val SPACE_SWALLOWING = ".,;:!?)"
        const val CLOSERS = ")]\"'"
    }
}
