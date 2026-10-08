// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DictionaryInstallerTest {
    @TempDir
    lateinit var tmp: File

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it)
        }

    private class FakeAssets(val files: Map<String, ByteArray>) : AssetSource {
        val opened = mutableListOf<String>()

        override fun open(path: String) =
            files[path]?.also { opened += path }?.inputStream() ?: throw FileNotFoundException(path)
    }

    private fun assets(
        version: String = "1",
        esBytes: ByteArray = "es-data".toByteArray(),
        esSha: String = sha(esBytes)
    ): FakeAssets {
        val index = "version=$version\nformat=aosp-combined-gz\nlanguages=es,en-US,en-GB\n" +
            "es.file=es.combined.gz\nes.sha256=$esSha\n" +
            "en-US.file=en-US.combined.gz\nen-US.sha256=${sha("us-data".toByteArray())}\n" +
            "en-GB.file=en-GB.combined.gz\nen-GB.sha256=${sha("gb-data".toByteArray())}\n"
        return FakeAssets(
            mapOf(
                "dictionaries/index.properties" to index.toByteArray(),
                "dictionaries/es.combined.gz" to esBytes,
                "dictionaries/en-US.combined.gz" to "us-data".toByteArray(),
                "dictionaries/en-GB.combined.gz" to "gb-data".toByteArray()
            )
        )
    }

    @Test
    fun `installs files and locates them by tag then by language`() {
        val target = File(tmp, "files/dictionaries")
        val locator = DictionaryInstaller(assets(), target).ensureInstalled()
        assertEquals("es-data", locator.fileFor(Locale.forLanguageTag("es-MX"))!!.readText())
        assertEquals("us-data", locator.fileFor(Locale.US)!!.readText())
        assertEquals("gb-data", locator.fileFor(Locale.UK)!!.readText())
        assertEquals("us-data", locator.fileFor(Locale.ENGLISH)!!.readText())
        assertNull(locator.fileFor(Locale.FRENCH))
        assertEquals("1", locator.version)
        assertEquals("aosp-combined-gz", locator.format)
        assertEquals(listOf("es", "en-US", "en-GB"), locator.languages)
    }

    @Test
    fun `installs only the requested languages`() {
        val source = assets()
        val target = File(tmp, "dictionaries")
        val locator = DictionaryInstaller(source, target).ensureInstalled(setOf("es"))
        assertNotNull(locator.fileFor(Locale.forLanguageTag("es")))
        assertNull(locator.fileFor(Locale.UK))
        assertEquals(
            listOf("dictionaries/index.properties", "dictionaries/es.combined.gz"),
            source.opened
        )
        // Asking later for another language installs just that one.
        val more = DictionaryInstaller(source, target).ensureInstalled(setOf("es", "en-GB"))
        assertNotNull(more.fileFor(Locale.UK))
        assertEquals(1, source.opened.count { it == "dictionaries/es.combined.gz" })
    }

    @Test
    fun `does not copy again while the markers match`() {
        val target = File(tmp, "dictionaries")
        val source = assets()
        DictionaryInstaller(source, target).ensureInstalled()
        val opensAfterFirst = source.opened.size
        DictionaryInstaller(source, target).ensureInstalled()
        assertEquals(opensAfterFirst + 1, source.opened.size) // only the index is read again
    }

    @Test
    fun `refreshes a file whose pin changed`() {
        val target = File(tmp, "dictionaries")
        DictionaryInstaller(assets("1"), target).ensureInstalled()
        val updated = DictionaryInstaller(
            assets("2", "es-new".toByteArray()),
            target
        ).ensureInstalled()
        assertEquals("es-new", updated.fileFor(Locale.forLanguageTag("es"))!!.readText())
        assertEquals("2", updated.version)
    }

    @Test
    fun `repairs a missing file even if the marker matches`() {
        val target = File(tmp, "dictionaries")
        DictionaryInstaller(assets(), target).ensureInstalled()
        assertTrue(File(target, "es.combined.gz").delete())
        val locator = DictionaryInstaller(assets(), target).ensureInstalled()
        assertNotNull(locator.fileFor(Locale.forLanguageTag("es")))
    }

    @Test
    fun `a file edited after install is not offered`() {
        val target = File(tmp, "dictionaries")
        DictionaryInstaller(assets(), target).ensureInstalled()
        File(target, "es.combined.gz.sha256").writeText("something else")
        val locator = DictionaryLocatorProbe.locator(assets(), target)
        assertNull(locator.fileFor(Locale.forLanguageTag("es")))
    }

    @Test
    fun `rejects corrupted assets and leaves nothing usable`() {
        val target = File(tmp, "dictionaries")
        val corrupt = assets(esSha = sha("other".toByteArray()))
        assertThrows(IOException::class.java) {
            DictionaryInstaller(corrupt, target).ensureInstalled()
        }
        assertFalse(File(target, "es.combined.gz").exists())
        assertFalse(File(target, "es.combined.gz.part").exists())
        assertFalse(File(target, "es.combined.gz.sha256").exists())
    }

    @Test
    fun `removes files that left the index`() {
        val target = File(tmp, "dictionaries")
        target.mkdirs()
        File(target, "en.wordlist.bin").writeText("old")
        File(target, "version").writeText("1")
        DictionaryInstaller(assets(), target).ensureInstalled(setOf("es"))
        assertEquals(setOf("es.combined.gz", "es.combined.gz.sha256"), target.list()!!.toSet())
    }

    @Test
    fun `index parsing reports missing keys`() {
        assertThrows(IllegalArgumentException::class.java) {
            DictionaryIndex.parse("version=1\nformat=x\n".byteInputStream())
        }
        val index = DictionaryIndex.parse(
            "version=1\nformat=x\nlanguages=es\nes.file=a\nes.sha256=b\n".byteInputStream()
        )
        assertEquals(DictionaryAsset("es", "a", "b"), index.assetFor("es"))
        assertNull(index.assetFor("en"))
    }

    /** Reads the locator of an installed directory without installing anything. */
    private object DictionaryLocatorProbe {
        fun locator(source: AssetSource, dir: File): DictionaryLocator = DictionaryLocator(
            DictionaryIndex.parse(source.open("dictionaries/index.properties")),
            dir
        )
    }
}
