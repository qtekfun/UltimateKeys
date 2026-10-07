// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.qtekfun.ultimatekeys.ime.logic.EditorConnection

/** Adapts the platform [InputConnection] to the keyboard logic. */
class AndroidEditorConnection(private val ic: InputConnection) : EditorConnection {
    override fun beginBatchEdit() {
        ic.beginBatchEdit()
    }

    override fun endBatchEdit() {
        ic.endBatchEdit()
    }

    override fun commitText(text: CharSequence) {
        ic.commitText(text, 1)
    }

    override fun setComposingText(text: CharSequence) {
        ic.setComposingText(text, 1)
    }

    override fun finishComposingText() {
        ic.finishComposingText()
    }

    override fun deleteSurroundingText(before: Int, after: Int) {
        ic.deleteSurroundingText(before, after)
    }

    override fun textBeforeCursor(length: Int): CharSequence =
        ic.getTextBeforeCursor(length, 0) ?: ""

    override fun textAfterCursor(length: Int): CharSequence = ic.getTextAfterCursor(length, 0) ?: ""

    override fun selectedText(): CharSequence = ic.getSelectedText(0) ?: ""

    override fun setSelection(start: Int, end: Int) {
        ic.setSelection(start, end)
    }

    override fun performEditorAction(actionId: Int) {
        ic.performEditorAction(actionId)
    }

    override fun sendEnterKey() {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    override fun cursorCapsMode(inputType: Int): Int = ic.getCursorCapsMode(inputType)
}
