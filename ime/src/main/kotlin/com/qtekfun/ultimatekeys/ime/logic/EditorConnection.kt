// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.logic

/**
 * The part of android.view.inputmethod.InputConnection the keyboard logic needs.
 * An interface so that the logic can be tested on the JVM with a fake editor.
 */
interface EditorConnection {
    fun beginBatchEdit()

    fun endBatchEdit()

    fun commitText(text: CharSequence)

    fun setComposingText(text: CharSequence)

    fun finishComposingText()

    fun deleteSurroundingText(before: Int, after: Int)

    fun textBeforeCursor(length: Int): CharSequence

    fun textAfterCursor(length: Int): CharSequence

    /** The selected text, or empty when the selection is collapsed. */
    fun selectedText(): CharSequence

    fun setSelection(start: Int, end: Int)

    fun performEditorAction(actionId: Int)

    /** Sends a hardware-style Enter key press, for editors that do not take text input. */
    fun sendEnterKey()

    /** Result of InputConnection.getCursorCapsMode, 0 when no capitalization is wanted. */
    fun cursorCapsMode(inputType: Int): Int
}
