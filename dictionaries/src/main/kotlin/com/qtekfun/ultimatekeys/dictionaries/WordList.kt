// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import java.io.InputStream
import java.util.zip.GZIPInputStream

/** One headword of a word list. */
data class WordEntry(
    val word: String,
    /** Relative frequency, 0..255 in the source lists. 0 means "valid but never suggested". */
    val frequency: Int,
    /** Free-form tags from the source (for example `abbreviation`), possibly empty. */
    val flags: Set<String> = emptySet(),
    val possiblyOffensive: Boolean = false,
    /** True for entries that exist only so the engine can correct them (typos), never to suggest. */
    val notAWord: Boolean = false
)

/** Header of a word list file. */
data class WordListHeader(val attributes: Map<String, String>) {
    val locale: String? get() = attributes["locale"]
    val version: String? get() = attributes["version"]
}

/** A parsed word list: a header and its words, in file order (most frequent first). */
data class WordList(val header: WordListHeader, val words: List<WordEntry>)

/**
 * Reader for the plain-text "combined" word list format used by the pinned sources:
 *
 * ```
 * dictionary=main:es,locale=es,description=Español,date=1414726268,version=54
 *  word=de,f=225,flags=,originalFreq=225
 *  word=sexo,f=128,flags=,originalFreq=128,possibly_offensive=true
 *   shortcut=...,f=whitelist     (ignored)
 *   bigram=...,f=...             (ignored; not present in the pinned lists)
 * ```
 *
 * Lines are `key=value` pairs separated by commas. Only the first-level `word=` lines become entries.
 */
object WordListParser {
    private const val WORD_PREFIX = " word="
    private const val HEADER_PREFIX = "dictionary="

    /** Parses a gzip-compressed word list. */
    fun parseGzip(input: InputStream): WordList = GZIPInputStream(input).use { parse(it) }

    /**
     * Gives [block] the words of a gzip-compressed list as a lazy sequence, in file order, without holding the whole
     * list in memory (the biggest list has over a million words). The sequence is only valid inside [block].
     */
    fun <R> streamGzip(input: InputStream, block: (Sequence<WordEntry>) -> R): R =
        GZIPInputStream(input).bufferedReader(Charsets.UTF_8).useLines { lines ->
            block(lines.filter { it.startsWith(WORD_PREFIX) }.map(::parseWord))
        }

    fun parse(input: InputStream): WordList {
        var header: WordListHeader? = null
        val words = ArrayList<WordEntry>()
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith(WORD_PREFIX) -> words += parseWord(line)
                    line.startsWith(HEADER_PREFIX) && header == null -> header = parseHeader(line)
                }
            }
        }
        return WordList(
            header ?: throw IllegalArgumentException("Missing dictionary header"),
            words
        )
    }

    internal fun parseHeader(line: String): WordListHeader = WordListHeader(
        line.split(',').mapNotNull { part ->
            part.indexOf('=').takeIf { it > 0 }?.let {
                part.substring(0, it) to
                    part.substring(it + 1)
            }
        }.toMap()
    )

    internal fun parseWord(line: String): WordEntry {
        val body = line.substring(WORD_PREFIX.length)
        // The headword itself may contain commas, so anchor on the frequency attribute.
        val freqAt = body.indexOf(",f=")
        require(freqAt > 0) { "Malformed word line: $line" }
        val word = body.substring(0, freqAt)
        val attributes = body.substring(freqAt + 1).split(',').mapNotNull { part ->
            part.indexOf('=').takeIf { it > 0 }?.let {
                part.substring(0, it) to
                    part.substring(it + 1)
            }
        }.toMap()
        val frequency =
            requireNotNull(attributes["f"]?.toIntOrNull()) { "Missing frequency: $line" }
        val flags = attributes["flags"].orEmpty().split(':').filter { it.isNotEmpty() }.toSet()
        return WordEntry(
            word = word,
            frequency = frequency,
            flags = flags,
            possiblyOffensive = attributes["possibly_offensive"] == "true",
            notAWord = attributes["not_a_word"] == "true"
        )
    }
}
