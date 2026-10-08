// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

/** A candidate as the native engine reports it, before any Kotlin-side post-processing. */
internal data class RawSuggestion(val word: String, val score: Int, val kind: Int) {
    val isAppropriateForAutoCorrection: Boolean
        get() = kind and KIND_FLAG_APPROPRIATE_FOR_AUTOCORRECTION != 0

    val baseKind: Int get() = kind and KIND_MASK

    companion object {
        const val KIND_MASK = 0xFF
        const val KIND_TYPED = 0
        const val KIND_PREDICTION = 8

        // Equal to Dictionary::KIND_FLAG_APPROPRIATE_FOR_AUTOCORRECTION in the native code.
        const val KIND_FLAG_APPROPRIATE_FOR_AUTOCORRECTION = 0x10000000
    }
}

/**
 * The part of the native engine [AospSuggestionEngine] needs, in Kotlin types. [JniNativeBridge]
 * is the real implementation (thin array plumbing over [NativeEngine]); tests use a fake, since
 * native code cannot run on the JVM.
 *
 * Handles are opaque non-zero numbers; `0` means failure. Implementations are not thread-safe:
 * the caller serializes access.
 */
internal interface NativeBridge {
    /** Maps [length] bytes at [offset] of [path] (a file, or a directory for updatable ones). */
    fun openDictionary(path: String, offset: Long, length: Long, updatable: Boolean): Long

    /** Creates an empty updatable dictionary in the directory [path]. */
    fun createEmptyDictionary(
        path: String,
        locale: String,
        attributes: Map<String, String>
    ): Boolean

    fun closeDictionary(dictionary: Long)

    fun newSession(locale: String, dictionarySize: Long): Long

    fun releaseSession(session: Long)

    fun newProximityInfo(geometry: KeyboardGeometry): Long

    fun releaseProximityInfo(proximityInfo: Long)

    /**
     * Candidates for [composing] after [context] (oldest word first), or next-word predictions when
     * [composing] is empty. Order is unspecified.
     */
    fun suggest(
        dictionary: Long,
        session: Long,
        proximityInfo: Long,
        composing: String,
        context: List<String>
    ): List<RawSuggestion>

    /** Unigram probability of [word] (0..255), or [NOT_A_PROBABILITY] when it is not in the dictionary. */
    fun probability(dictionary: Long, word: String): Int

    /**
     * Records that [word] was typed after [context]; [timestampSeconds] drives forgetting.
     * [isValidWord] says whether the word is known elsewhere: unknown words are only promoted to
     * suggestions after the engine has seen them repeatedly.
     */
    fun learnWord(
        dictionary: Long,
        context: List<String>,
        word: String,
        isValidWord: Boolean,
        timestampSeconds: Int
    ): Boolean

    fun addUnigram(dictionary: Long, word: String, probability: Int, timestampSeconds: Int): Boolean

    fun removeUnigram(dictionary: Long, word: String): Boolean

    /** Every word stored in the dictionary, in storage order. */
    fun words(dictionary: Long): List<String>

    /** Writes an updatable dictionary to the directory [path], compacting it when needed. */
    fun flush(dictionary: Long, path: String): Boolean

    /**
     * Garbage-collects the updatable dictionary into the directory [path] when its write buffer is nearly full.
     * Must be called regularly while adding many words. Returns false when a needed collection failed.
     */
    fun compactIfNeeded(dictionary: Long, path: String): Boolean

    /** The engine's own 0..1 confidence that [candidate] is what the user meant by [typed]. */
    fun normalizedScore(typed: String, candidate: String, score: Int): Float

    companion object {
        const val NOT_A_PROBABILITY = -1
    }
}
