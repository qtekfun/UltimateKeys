// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import com.qtekfun.ultimatekeys.engine.WordFrequency
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import java.util.zip.GZIPOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    private fun locator(version: String, esExtra: String = ""): DictionaryLocator {
        val es = gz(
            "dictionary=main:es,locale=es,description=t,date=1,version=1\n" +
                " word=hola,f=200,flags=,originalFreq=200\n" +
                " word=malo,f=100,flags=,originalFreq=100,possibly_offensive=true\n" +
                " word=otro,f=50,flags=,originalFreq=50\n" +
                " word=nada,f=0,not_a_word=true\n" +
                " word=raro,f=10,flags=,originalFreq=10\n" + esExtra
        )
        val ru = gz(
            "dictionary=main:ru,locale=ru,description=t,date=1,version=1\n" +
                " word=привет,f=200,flags=,originalFreq=200\n"
        )
        val index = "version=$version\nformat=aosp-combined-gz\nlanguages=es,ru,pt-BR\n" +
            "es.file=es.combined.gz\nes.sha256=${sha(es)}\n" +
            "ru.file=ru.combined.gz\nru.sha256=${sha(ru)}\n" +
            "pt-BR.file=pt.combined.gz\npt-BR.sha256=${sha(ru)}\n"
        val files = mapOf(
            "dictionaries/index.properties" to index.toByteArray(),
            "dictionaries/es.combined.gz" to es,
            "dictionaries/ru.combined.gz" to ru,
            "dictionaries/pt.combined.gz" to ru
        )
        val assets = AssetSource { path ->
            files[path]?.inputStream() ?: throw FileNotFoundException(path)
        }
        return DictionaryInstaller(assets, File(tmp, "installed")).ensureInstalled()
    }

    private val built = mutableListOf<Pair<String, List<String>>>()
    private var succeed = true

    private val build = { dir: File, locale: Locale, words: Sequence<WordFrequency> ->
        dir.mkdirs()
        built += locale.toLanguageTag() to words.map { it.word }.toList()
        succeed
    }

    @Test
    fun `builds only the requested languages, once per word list, skipping offensive words`() {
        val out = File(tmp, "binary")
        val dictionaries = BinaryDictionaries(locator("1"), out, build = build)

        val ready = dictionaries.prepare(listOf("es"))

        assertEquals(setOf("es"), ready)
        assertEquals(listOf("es" to listOf("hola", "otro", "raro")), built)
        val es = dictionaries.locator().mainDictionary(Locale.forLanguageTag("es-MX"))
        assertNotNull(es)
        assertTrue(es!!.isDirectory)
        assertNull(dictionaries.locator().mainDictionary(Locale.forLanguageTag("ru")))
        assertFalse(dictionaries.isReady("ru"))

        BinaryDictionaries(locator("1"), out, build = build).prepare(listOf("es"))
        assertEquals(1, built.size)

        assertEquals(setOf("es", "ru"), dictionaries.prepare(listOf("es", "ru")))
        assertEquals("ru" to listOf("привет"), built.last())
        assertEquals(2, built.size)
    }

    @Test
    fun `a changed word list rebuilds, a version bump alone does not`() {
        val out = File(tmp, "binary")
        BinaryDictionaries(locator("1"), out, build = build).prepare(listOf("es"))
        BinaryDictionaries(locator("2"), out, build = build).prepare(listOf("es"))
        assertEquals(1, built.size)
        File(tmp, "installed").deleteRecursively()
        BinaryDictionaries(
            locator("3", " word=nuevo,f=5,flags=,originalFreq=5\n"),
            out,
            build = build
        )
            .prepare(listOf("es"))
        assertEquals(2, built.size)
    }

    @Test
    fun `keeps only the most frequent words`() {
        val out = File(tmp, "binary")
        BinaryDictionaries(locator("1"), out, maxWords = 2, build = build).prepare(listOf("es"))
        assertEquals(listOf("hola", "otro"), built.single().second)
        // A different cap is a different dictionary.
        BinaryDictionaries(locator("1"), out, maxWords = 3, build = build).prepare(listOf("es"))
        assertEquals(2, built.size)
    }

    @Test
    fun `a failed build is retried and not offered to the engine`() {
        succeed = false
        val out = File(tmp, "binary")
        val dictionaries = BinaryDictionaries(locator("1"), out, build = build)
        assertEquals(emptySet<String>(), dictionaries.prepare(listOf("es")))
        assertNull(dictionaries.locator().mainDictionary(Locale.forLanguageTag("es")))
        assertFalse(File(out, "es").exists())
        succeed = true
        assertEquals(setOf("es"), dictionaries.prepare(listOf("es")))
        assertNotNull(dictionaries.locator().mainDictionary(Locale.forLanguageTag("es")))
    }

    @Test
    fun `an interrupted build without its stamp is not usable`() {
        val out = File(tmp, "binary")
        File(out, "es").mkdirs()
        val dictionaries = BinaryDictionaries(locator("1"), out, build = build)
        assertNull(dictionaries.locator().mainDictionary(Locale.forLanguageTag("es")))
    }

    @Test
    fun `unknown languages and languages whose list is not installed are skipped`() {
        val out = File(tmp, "binary")
        val dictionaries = BinaryDictionaries(locator("1"), out, build = build)
        // locator() installed everything, so remove the Portuguese list to simulate a missing one.
        File(tmp, "installed/pt.combined.gz").delete()
        assertEquals(emptySet<String>(), dictionaries.prepare(listOf("xx", "pt-BR")))
        assertTrue(built.isEmpty())
    }

    @Test
    fun `prune deletes the dictionaries that are not kept`() {
        val out = File(tmp, "binary")
        val dictionaries = BinaryDictionaries(locator("1"), out, build = build)
        dictionaries.prepare(listOf("es", "ru"))
        File(out, "gone").mkdirs()

        dictionaries.prune(listOf("ru"))

        assertEquals(setOf("ru", "ru.stamp"), out.list()!!.toSet())
        assertTrue(dictionaries.isReady("ru"))
        assertFalse(dictionaries.isReady("es"))
    }
}
