// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import com.qtekfun.ultimatekeys.languages.LanguageCatalog
import com.qtekfun.ultimatekeys.layouts.CharKey
import com.qtekfun.ultimatekeys.layouts.LayoutRepository
import java.io.File
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Integration test over the real, checksum-verified word lists produced by `fetchDictionaries`. */
class PinnedDictionariesTest {
    private val assetsRoot =
        File(requireNotNull(System.getProperty("dictionaries.assets")) { "not set by Gradle" })

    private val source = AssetSource { path -> File(assetsRoot, path).inputStream() }

    @TempDir
    lateinit var tmp: File

    private fun installed() =
        DictionaryInstaller(source, File(tmp, "dictionaries")).ensureInstalled()

    @Test
    fun `installs the pinned dictionaries and they parse`() {
        val locator = installed()
        assertEquals("aosp-combined-gz", locator.format)
        val spanish = WordListParser.parseGzip(
            locator.fileFor(Locale.forLanguageTag("es"))!!.inputStream()
        )
        val english = WordListParser.parseGzip(locator.fileFor(Locale.US)!!.inputStream())
        assertEquals("es", spanish.header.locale)
        assertEquals("en_US", english.header.locale)
        assertTrue(spanish.words.size > 100_000)
        assertTrue(english.words.size > 100_000)
        assertTrue(spanish.words.any { it.word == "señor" })
        assertTrue(english.words.any { it.word == "the" && it.frequency > 200 })
        assertTrue(spanish.words.all { it.frequency in 0..255 })
    }

    @Test
    fun `the catalog and the pins list exactly the same languages`() {
        val locator = installed()
        val catalog = LanguageCatalog.all.map { it.dictionaryId }.sorted()
        assertEquals(catalog, locator.languages.sorted())
    }

    @Test
    fun `every list parses, is big enough and is sorted by frequency`() {
        val locator = installed()
        LanguageCatalog.all.forEach { language ->
            val list = WordListParser.parseGzip(locator.fileFor(language.locale)!!.inputStream())
            assertTrue(list.words.size > 30_000, "${language.tag} has ${list.words.size} words")
            assertTrue(list.words.all { it.frequency in 0..255 }, language.tag)
            // The word cap keeps the head of the list, so the head must be the most frequent words.
            val head = list.words.take(MAX_CHECKED).map { it.frequency }
            assertEquals(head.sortedDescending(), head, "${language.tag} is not sorted")
            assertEquals(
                list.words.maxOf { it.frequency },
                list.words.first().frequency,
                language.tag
            )
        }
    }

    @Test
    fun `the common words of each language can be typed on its default layout`() {
        val locator = installed()
        LanguageCatalog.all.forEach { language ->
            val keys = LayoutRepository.load(language.defaultLayoutId).rows.flatMap { it.keys }
                .filterIsInstance<CharKey>()
            val reachable = keys.flatMap { listOf(it.output) + it.alternatives }
                .flatMap { it.lowercase(Locale.ROOT).toList() }.toSet()
            val punctuation = "'’-.·"
            val list = WordListParser.parseGzip(locator.fileFor(language.locale)!!.inputStream())
            val common = list.words.filter { !it.notAWord }.take(COMMON_WORDS)
            val untypable = common.filter { w ->
                w.word.lowercase(language.locale)
                    .any { it !in reachable && it !in punctuation && !it.isDigit() }
            }
            val missing = untypable.flatMap { w ->
                w.word.lowercase(language.locale).filter { it !in reachable && it !in punctuation }
                    .toList()
            }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8)
            assertTrue(
                untypable.size <= common.size / 100,
                "${language.tag}: ${untypable.size} of ${common.size} common words need keys " +
                    "the layout lacks: $missing, e.g. ${untypable.take(5).map { it.word }}"
            )
        }
    }

    private companion object {
        const val MAX_CHECKED = 5000
        const val COMMON_WORDS = 20_000
    }
}
