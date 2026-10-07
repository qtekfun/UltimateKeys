// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

import java.io.File

/** In-memory [NativeBridge]: dictionaries are word lists with scripted suggestion results. */
internal class FakeNativeBridge : NativeBridge {
    class Dict(val path: String, val updatable: Boolean) {
        val probabilities = linkedMapOf<String, Int>()
        val learned = mutableListOf<Triple<List<String>, String, Boolean>>()
        var flushes = 0
        var closed = false
        var typingResults: (String, List<String>) -> List<RawSuggestion> = { _, _ -> emptyList() }
        var predictionResults: (List<String>) -> List<RawSuggestion> = { emptyList() }
    }

    private var nextHandle = 1L
    val dictionaries = mutableMapOf<Long, Dict>()
    val openedPaths = mutableListOf<String>()
    var normalizedScore = 0.5f
    var liveSessions = 0
    var liveProximityInfos = 0

    /** Scripts what the main dictionary at [path] returns; applied when it is opened. */
    val mainScripts = mutableMapOf<String, (Dict) -> Unit>()

    fun dictAt(pathSuffix: String): Dict = dictionaries.values.last { it.path.endsWith(pathSuffix) }

    override fun openDictionary(
        path: String,
        offset: Long,
        length: Long,
        updatable: Boolean
    ): Long {
        if (updatable && !File(path, MARKER).exists()) return 0L
        if (!updatable && !mainScripts.containsKey(path)) return 0L
        val dict = Dict(path, updatable)
        mainScripts[path]?.invoke(dict)
        val handle = nextHandle++
        dictionaries[handle] = dict
        openedPaths += path
        return handle
    }

    override fun createEmptyDictionary(
        path: String,
        locale: String,
        attributes: Map<String, String>
    ): Boolean = File(path, MARKER).createNewFile()

    override fun closeDictionary(dictionary: Long) {
        dictionaries.getValue(dictionary).closed = true
    }

    override fun newSession(locale: String, dictionarySize: Long): Long = (++liveSessions).toLong()

    override fun releaseSession(session: Long) {
        liveSessions--
    }

    override fun newProximityInfo(geometry: KeyboardGeometry): Long =
        (++liveProximityInfos).toLong()

    override fun releaseProximityInfo(proximityInfo: Long) {
        liveProximityInfos--
    }

    override fun suggest(
        dictionary: Long,
        session: Long,
        proximityInfo: Long,
        composing: String,
        context: List<String>
    ): List<RawSuggestion> {
        val dict = dictionaries.getValue(dictionary)
        return if (composing.isEmpty()) {
            dict.predictionResults(
                context
            )
        } else {
            dict.typingResults(composing, context)
        }
    }

    override fun probability(dictionary: Long, word: String): Int =
        dictionaries.getValue(dictionary).probabilities[word] ?: NativeBridge.NOT_A_PROBABILITY

    override fun learnWord(
        dictionary: Long,
        context: List<String>,
        word: String,
        isValidWord: Boolean,
        timestampSeconds: Int
    ): Boolean {
        dictionaries.getValue(dictionary).learned += Triple(context, word, isValidWord)
        return true
    }

    override fun addUnigram(
        dictionary: Long,
        word: String,
        probability: Int,
        timestampSeconds: Int
    ): Boolean {
        dictionaries.getValue(dictionary).probabilities[word] = probability
        return true
    }

    override fun removeUnigram(dictionary: Long, word: String): Boolean =
        dictionaries.getValue(dictionary).probabilities.remove(word) != null

    override fun words(dictionary: Long): List<String> =
        dictionaries.getValue(dictionary).probabilities.keys.toList()

    override fun flush(dictionary: Long, path: String): Boolean {
        dictionaries.getValue(dictionary).flushes++
        return true
    }

    override fun normalizedScore(typed: String, candidate: String, score: Int): Float =
        normalizedScore

    companion object {
        const val MARKER = "header"
        const val AUTOCORRECT_OK = RawSuggestion.KIND_FLAG_APPROPRIATE_FOR_AUTOCORRECTION or 1
    }
}
