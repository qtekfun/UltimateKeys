// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import java.util.zip.GZIPOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BinaryDictionariesTest {
    @TempDir
    lateinit var tmp: File

    private fun gz(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
        return out.toByteArray()
    }

    private fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun locator(version: String): DictionaryLocator {
        val es = gz(
            "dictionary=main:es,locale=es,description=t,date=1,version=1\n" +
                " word=hola,f=200,flags=,originalFreq=200\n" +
                " word=malo,f=100,flags=,originalFreq=100,possibly_offensive=true\n" +
                " word=otro,f=50,flags=,originalFreq=50\n"
        )
        val en =
            gz(
                "dictionary=main:en,locale=en,description=t,date=1,version=1\n word=hello,f=200,flags=,originalFreq=200\n"
            )
        val index = "version=$version\nformat=aosp-combined-gz\nlanguages=es,en\n" +
            "es.file=es.combined.gz\nes.sha256=${sha(
                es
            )}\nen.file=en.combined.gz\nen.sha256=${sha(en)}\n"
        val files = mapOf(
            "dictionaries/index.properties" to index.toByteArray(),
            "dictionaries/es.combined.gz" to es,
            "dictionaries/en.combined.gz" to en
        )
        val assets = AssetSource { path ->
            files[path]?.inputStream()
                ?: throw FileNotFoundException(path)
        }
        return DictionaryInstaller(assets, File(tmp, "installed")).ensureInstalled()
    }

    @Test
    fun `builds each language once per version and skips offensive words`() {
        val built = mutableListOf<Pair<String, List<String>>>()
        val build = {
                dir: File,
                locale: Locale,
                words: Sequence<com.qtekfun.ultimatekeys.engine.WordFrequency>
            ->
            dir.mkdirs()
            built += locale.language to words.map { it.word }.toList()
            true
        }
        val out = File(tmp, "binary")
        val engineLocator = BinaryDictionaries(locator("1"), out, build).prepare()
        assertEquals(listOf("es" to listOf("hola", "otro"), "en" to listOf("hello")), built)
        val es = engineLocator.mainDictionary(Locale.forLanguageTag("es-MX"))
        assertNotNull(es)
        assertTrue(es!!.isDirectory)
        assertNull(engineLocator.mainDictionary(Locale.FRENCH))

        BinaryDictionaries(locator("1"), out, build).prepare()
        assertEquals(2, built.size)

        BinaryDictionaries(locator("2"), out, build).prepare()
        assertEquals(4, built.size)
    }

    @Test
    fun `a failed build is retried and not offered to the engine`() {
        var ok = false
        val build = {
                dir: File,
                _: Locale,
                _: Sequence<com.qtekfun.ultimatekeys.engine.WordFrequency>
            ->
            dir.mkdirs()
            ok
        }
        val out = File(tmp, "binary")
        val first = BinaryDictionaries(locator("1"), out, build).prepare()
        assertNull(first.mainDictionary(Locale.forLanguageTag("es")))
        ok = true
        val second = BinaryDictionaries(locator("1"), out, build).prepare()
        assertNotNull(second.mainDictionary(Locale.forLanguageTag("es")))
    }
}
