// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import com.qtekfun.ultimatekeys.engine.DictionaryFile
import com.qtekfun.ultimatekeys.engine.DictionaryLocator as EngineLocator
import com.qtekfun.ultimatekeys.engine.WordFrequency
import java.io.File
import java.util.Locale

/**
 * Turns the installed word lists into the dictionaries the native engine opens, once per data
 * version (ADR 0008). [build] writes one dictionary directory (in the app it calls the engine's
 * dictionary builder). Run [prepare] on a background thread: a full list takes seconds.
 */
class BinaryDictionaries(
    private val source: DictionaryLocator,
    private val outDir: File,
    private val build: (File, Locale, Sequence<WordFrequency>) -> Boolean
) {
    /** Builds what is missing or out of date and returns the locator for the engine. */
    fun prepare(): EngineLocator {
        source.languages.forEach { language ->
            val locale = Locale.forLanguageTag(language)
            if (!isCurrent(language)) buildOne(language, locale)
        }
        return EngineLocator { locale ->
            val language = locale.language
            if (isCurrent(
                    language
                )
            ) {
                DictionaryFile(
                    dirFor(language).absolutePath,
                    0L,
                    0L,
                    isDirectory = true
                )
            } else {
                null
            }
        }
    }

    private fun buildOne(language: String, locale: Locale) {
        val file = source.fileFor(locale) ?: return
        markerFor(language).delete()
        val words = file.inputStream().use { WordListParser.parseGzip(it) }.words.asSequence()
            .filter { !it.notAWord && !it.possiblyOffensive }
            .map { WordFrequency(it.word, it.frequency) }
        if (build(dirFor(language), locale, words)) markerFor(language).writeText(source.version)
    }

    private fun isCurrent(language: String): Boolean {
        val marker = markerFor(language)
        return marker.isFile && marker.readText() == source.version && dirFor(language).isDirectory
    }

    private fun dirFor(language: String) = File(outDir, language)

    private fun markerFor(language: String) = File(outDir, "$language.version")
}
