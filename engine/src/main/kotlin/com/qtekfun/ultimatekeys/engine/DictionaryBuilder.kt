// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

import java.io.File
import java.util.Locale

/** A word and its frequency (0..255), the input for [DictionaryBuilder]. */
data class WordFrequency(val word: String, val frequency: Int)

/**
 * Builds a main dictionary for the native engine from a plain word list, on the device, by adding
 * the words to a new updatable dictionary and writing it out (see ADR 0008). Slow for a full list
 * (seconds): run it once per data version on a background thread, never on the main thread.
 */
class DictionaryBuilder internal constructor(
    private val bridge: NativeBridge,
    private val report: (String) -> Unit
) {
    /** [report] receives a line for every failed step, for the log. */
    constructor(report: (String) -> Unit = {}) : this(JniNativeBridge, report)

    /**
     * Writes the dictionary for [locale] into [directory] (created or replaced). Returns false and
     * leaves nothing behind when any native step fails.
     */
    fun build(directory: File, locale: Locale, words: Sequence<WordFrequency>): Boolean {
        directory.deleteRecursively()
        if (!directory.mkdirs()) {
            report("cannot create $directory")
            return false
        }
        val attributes = mapOf(
            "dictionary" to "main:" + locale.language,
            "locale" to locale.language
        )
        val created = bridge.createEmptyDictionary(
            directory.absolutePath,
            locale.language,
            attributes
        )
        val handle = if (created) {
            bridge.openDictionary(
                directory.absolutePath,
                0L,
                0L,
                updatable = true
            )
        } else {
            0L
        }
        if (handle == 0L) {
            report("create=$created open=failed for $directory")
            directory.deleteRecursively()
            return false
        }
        val ok = try {
            addAll(handle, directory.absolutePath, words) &&
                bridge.flush(handle, directory.absolutePath)
        } finally {
            bridge.closeDictionary(handle)
        }
        if (!ok) {
            report("adding words or flushing failed for $directory")
            directory.deleteRecursively()
        }
        return ok
    }

    private fun addAll(handle: Long, path: String, words: Sequence<WordFrequency>): Boolean {
        val now = (System.currentTimeMillis() / MILLIS_PER_SECOND).toInt()
        var added = 0
        for (entry in words) {
            if (entry.word.isBlank()) continue
            if (added % PROGRESS_EVERY == 0) report("adding word #$added: ${entry.word}")
            if (bridge.addUnigram(
                    handle,
                    entry.word,
                    entry.frequency.coerceIn(0, MAX_FREQUENCY),
                    now
                )
            ) {
                added++
                // The write buffer is small: compact it often or the native side overruns it.
                if (added % COMPACT_EVERY == 0 &&
                    !bridge.compactIfNeeded(handle, path)
                ) {
                    return false
                }
            }
        }
        return added > 0
    }

    private companion object {
        const val MAX_FREQUENCY = 255
        const val COMPACT_EVERY = 256
        const val PROGRESS_EVERY = 2000
        const val MILLIS_PER_SECOND = 1000L
    }
}
