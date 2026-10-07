// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.logic

import android.text.InputType

/** An in-memory editor with a selection and a composing region, like a real text field. */
class FakeEditorConnection(initial: String = "") : EditorConnection {
    var text = StringBuilder(initial)
        private set
    var selStart = initial.length
        private set
    var selEnd = initial.length
        private set
    private var composingStart = -1
    private var composingEnd = -1

    val actions = mutableListOf<Int>()
    var enterKeys = 0
        private set
    var batchDepth = 0
        private set

    val composingText: String
        get() = if (composingStart < 0) "" else text.substring(composingStart, composingEnd)

    val cursor: Int get() = selStart

    override fun beginBatchEdit() {
        batchDepth++
    }

    override fun endBatchEdit() {
        batchDepth--
        check(batchDepth >= 0) { "Unbalanced batch edit" }
    }

    override fun commitText(text: CharSequence) {
        replaceTarget(text)
        composingStart = -1
        composingEnd = -1
    }

    override fun setComposingText(text: CharSequence) {
        val start = if (composingStart >= 0) composingStart else minOf(selStart, selEnd)
        replaceTarget(text)
        composingStart = start
        composingEnd = start + text.length
    }

    override fun finishComposingText() {
        composingStart = -1
        composingEnd = -1
    }

    override fun deleteSurroundingText(before: Int, after: Int) {
        val from = maxOf(0, selStart - before)
        val to = minOf(text.length, selEnd + after)
        text.delete(selEnd, to)
        text.delete(from, selStart)
        selStart = from
        selEnd = from
    }

    override fun textBeforeCursor(length: Int): CharSequence = text.substring(
        maxOf(
            0,
            selStart - length
        ),
        selStart
    )

    override fun textAfterCursor(length: Int): CharSequence = text.substring(
        selEnd,
        minOf(
            text.length,
            selEnd + length
        )
    )

    override fun selectedText(): CharSequence =
        text.substring(minOf(selStart, selEnd), maxOf(selStart, selEnd))

    override fun setSelection(start: Int, end: Int) {
        selStart = start.coerceIn(0, text.length)
        selEnd = end.coerceIn(0, text.length)
        composingStart = -1
        composingEnd = -1
    }

    override fun performEditorAction(actionId: Int) {
        actions += actionId
    }

    override fun sendEnterKey() {
        enterKeys++
    }

    override fun cursorCapsMode(inputType: Int): Int {
        val before = text.substring(0, selStart)
        var mode = 0
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS !=
            0
        ) {
            mode = mode or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 &&
            (before.isEmpty() || before.last().isWhitespace())
        ) {
            mode = mode or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 && atSentenceStart(before)) {
            mode = mode or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        return mode
    }

    private fun atSentenceStart(before: String): Boolean {
        val trimmed = before.trimEnd()
        if (trimmed.isEmpty()) return true
        return trimmed.length < before.length && trimmed.last() in ".!?"
    }

    private fun replaceTarget(replacement: CharSequence) {
        val (from, to) = if (composingStart >=
            0
        ) {
            composingStart to composingEnd
        } else {
            minOf(selStart, selEnd) to
                maxOf(selStart, selEnd)
        }
        text.replace(from, to, replacement.toString())
        selStart = from + replacement.length
        selEnd = selStart
    }
}
