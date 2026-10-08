// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import com.qtekfun.ultimatekeys.engine.DictionaryFile
import com.qtekfun.ultimatekeys.engine.DictionaryLocator as EngineLocator
import com.qtekfun.ultimatekeys.engine.WordFrequency
import java.io.File
import java.util.Locale

/**
 * Turns the installed word lists into the dictionaries the native engine opens (ADR 0008). [build] writes one
 * dictionary directory (in the app it calls the engine's dictionary builder). Building takes seconds per language,
 * so [prepare] only builds the languages it is asked for (the enabled ones), skips what is already current, and must
 * run on a background thread.
 *
 * A dictionary is only offered to the engine when its marker file, written last and removed first, says it was built
 * from the current word list: a build that fails or is interrupted never leaves a usable-looking directory.
 *
 * @param maxWords most frequent words kept per language. The updatable format has size limits (see the vendored
 *   engine's `MODIFICATIONS.md`) and the longest list has over a million words, mostly inflections nobody types.
 */
class BinaryDictionaries(
    private val source: DictionaryLocator,
    private val outDir: File,
    private val maxWords: Int = MAX_WORDS,
    private val build: (File, Locale, Sequence<WordFrequency>) -> Boolean
) {
    /**
     * Builds what is missing or out of date for [languages] (tags) and returns the ones that are ready afterwards.
     * A language whose word list is not installed, or whose build fails, is left out (and retried next time).
     */
    fun prepare(languages: Collection<String>): Set<String> {
        languages.forEach { language ->
            val asset = source.assetFor(Locale.forLanguageTag(language))
            if (asset != null && !isCurrent(asset)) buildOne(asset)
        }
        return languages.filter { isReady(it) }.toSet()
    }

    fun isReady(language: String): Boolean =
        source.assetFor(Locale.forLanguageTag(language))?.let(::isCurrent) == true

    /** What the engine uses: only the dictionaries that are ready. */
    fun locator(): EngineLocator = EngineLocator { locale ->
        source.assetFor(locale)?.takeIf(::isCurrent)?.let {
            DictionaryFile(dirFor(it).absolutePath, 0L, 0L, isDirectory = true)
        }
    }

    /** Deletes the built dictionaries of every language except [keep] (and of languages that left the index). */
    fun prune(keep: Collection<String>) {
        val keepNames = keep.mapNotNull { source.assetFor(Locale.forLanguageTag(it)) }
            .flatMap { listOf(it.language, it.language + MARKER_SUFFIX) }.toSet()
        outDir.listFiles()?.filter { it.name !in keepNames }?.forEach { it.deleteRecursively() }
    }

    private fun buildOne(asset: DictionaryAsset) {
        val file = source.fileFor(Locale.forLanguageTag(asset.language)) ?: return
        markerFor(asset).delete()
        val locale = Locale.forLanguageTag(asset.language)
        val built = file.inputStream().use { input ->
            WordListParser.streamGzip(input) { entries ->
                build(
                    dirFor(asset),
                    locale,
                    entries.filter { !it.notAWord && !it.possiblyOffensive }
                        .take(maxWords)
                        .map { WordFrequency(it.word, it.frequency) }
                )
            }
        }
        if (built) {
            markerFor(asset).writeText(stamp(asset))
        } else {
            dirFor(asset).deleteRecursively()
        }
    }

    private fun isCurrent(asset: DictionaryAsset): Boolean {
        val marker = markerFor(asset)
        return marker.isFile && marker.readText() == stamp(asset) && dirFor(asset).isDirectory
    }

    /** What a built dictionary was made from: the word list and the rules applied to it. */
    private fun stamp(asset: DictionaryAsset) = "${asset.sha256}/$maxWords"

    private fun dirFor(asset: DictionaryAsset) = File(outDir, asset.language)

    private fun markerFor(asset: DictionaryAsset) = File(outDir, asset.language + MARKER_SUFFIX)

    companion object {
        /** Words kept per language. Spanish, the biggest list that was verified on a device, has 236,000. */
        const val MAX_WORDS = 250_000
        private const val MARKER_SUFFIX = ".stamp"
    }
}
