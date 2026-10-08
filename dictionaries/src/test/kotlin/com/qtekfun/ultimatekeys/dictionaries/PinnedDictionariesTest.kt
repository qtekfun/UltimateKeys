// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

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

    @Test
    fun `installs the pinned dictionaries and they parse`() {
        val locator = DictionaryInstaller(source, File(tmp, "dictionaries")).ensureInstalled()
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
}
