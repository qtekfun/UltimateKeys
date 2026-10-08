// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.logic

/** What [InputLogic] tells the suggestion side, and asks it, while the user types. */
interface SuggestionHook {
    /** The word being composed changed ([composing] is empty between words: predict the next). */
    fun onComposingChanged(composing: String, contextBefore: String)

    /** The correction to apply when [composing] is ended by a space or punctuation, if any. */
    fun autoCorrectFor(composing: String): String?

    /** A word was finished (typed, corrected or picked). */
    fun onWordFinished(word: String, contextBefore: String, corrected: Boolean)

    /** The user undid an autocorrection of [original]. */
    fun onAutoCorrectRejected(original: String)

    /**
     * [word] was typed by gesture; [alternatives] are the other candidates, best first. The word is
     * not finished yet (the person may still swap or take it back): [onWordFinished] follows later.
     */
    fun onGestureCommitted(word: String, contextBefore: String, alternatives: List<String>) = Unit
}
