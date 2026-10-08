// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.suggest

import com.qtekfun.ultimatekeys.engine.Suggestion
import com.qtekfun.ultimatekeys.engine.SuggestionEngine
import com.qtekfun.ultimatekeys.ime.logic.SuggestionHook
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the suggestion strip shows: left, centre (the best) and right slots; empty when unused. */
data class SuggestionState(
    val slots: List<String> = List(SLOTS) { "" },
    /** True while the slots are the alternatives of a word just typed by gesture (a tap swaps the word). */
    val gesture: Boolean = false
) {
    companion object {
        const val SLOTS = 3
        const val BEST = 1
    }
}

/**
 * Asks the engine for suggestions off the main thread and keeps the strip up to date. Implements
 * [SuggestionHook] so the typing logic can ask for the autocorrection synchronously: it answers
 * from the result already computed for the exact word being composed (no result: no correction).
 */
class SuggestionController(
    private val engine: SuggestionEngine,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
    /** False while private mode is on: nothing may be learned. */
    private val learningAllowed: () -> Boolean = { true }
) : SuggestionHook {
    private val mutableState = MutableStateFlow(SuggestionState())
    val state: StateFlow<SuggestionState> = mutableState.asStateFlow()

    @Volatile
    private var cache: Pair<String, List<Suggestion>>? = null
    private var job: Job? = null

    override fun onComposingChanged(composing: String, contextBefore: String) {
        job?.cancel()
        val context = ContextWords.from(contextBefore)
        job = scope.launch(dispatcher) {
            val loc = locale()
            val result = if (composing.isEmpty()) {
                engine.predictNext(context, loc)
            } else {
                engine.suggest(context, composing, loc)
            }
            cache = composing to result
            mutableState.value = SuggestionState(arrange(composing, result))
        }
    }

    override fun autoCorrectFor(composing: String): String? {
        val (word, result) = cache ?: return null
        if (word != composing) return null
        return result.firstOrNull { it.autoCorrect }?.let {
            CaseMatcher.match(composing, it.word, it.locale ?: locale())
        }
    }

    override fun onWordFinished(word: String, contextBefore: String, corrected: Boolean) {
        if (!learningAllowed() || word.isBlank()) return
        val context = ContextWords.from(contextBefore)
        scope.launch(dispatcher) { engine.learn(word, context, locale()) }
    }

    override fun onGestureCommitted(
        word: String,
        contextBefore: String,
        alternatives: List<String>
    ) {
        job?.cancel()
        cache = null
        val others = alternatives.filterNot { it.equals(word, ignoreCase = true) }
        mutableState.value = SuggestionState(
            listOf(others.getOrNull(0).orEmpty(), word, others.getOrNull(1).orEmpty()),
            gesture = true
        )
    }

    override fun onAutoCorrectRejected(original: String) {
        if (!learningAllowed()) return
        scope.launch(dispatcher) { engine.learn(original, emptyList(), locale()) }
    }

    private fun arrange(composing: String, result: List<Suggestion>): List<String> {
        val words = result.map { CaseMatcher.match(composing, it.word, it.locale ?: locale()) }.distinct()
        val best = words.firstOrNull() ?: return SuggestionState().slots
        val literalFirst = composing.isNotEmpty() && result.first().autoCorrect && composing != best
        val left = if (literalFirst) composing else words.getOrNull(1).orEmpty()
        val right = if (literalFirst) words.getOrNull(1).orEmpty() else words.getOrNull(2).orEmpty()
        return listOf(left, best, right)
    }
}
