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
        var opens = 0

        override fun open(path: String) =
            files[path]?.also { opens++ }?.inputStream() ?: throw FileNotFoundException(path)
    }

    private fun assets(
        version: String = "1",
        esBytes: ByteArray = "es-data".toByteArray(),
        esSha: String = sha(esBytes)
    ): FakeAssets {
        val index = "version=$version\nformat=aosp-combined-gz\nlanguages=es,en\n" +
            "es.file=es.combined.gz\nes.sha256=$esSha\n" +
            "en.file=en.combined.gz\nen.sha256=${sha("en-data".toByteArray())}\n"
        return FakeAssets(
            mapOf(
                "dictionaries/index.properties" to index.toByteArray(),
                "dictionaries/es.combined.gz" to esBytes,
                "dictionaries/en.combined.gz" to "en-data".toByteArray()
            )
        )
    }

    @Test
    fun `installs files and locates them by language`() {
        val target = File(tmp, "files/dictionaries")
        val locator = DictionaryInstaller(assets(), target).ensureInstalled()
        assertEquals("es-data", locator.fileFor(Locale.forLanguageTag("es-MX"))!!.readText())
        assertEquals("en-data", locator.fileFor(Locale.US)!!.readText())
        assertNull(locator.fileFor(Locale.FRENCH))
        assertEquals("1", locator.version)
        assertEquals("aosp-combined-gz", locator.format)
        assertEquals(listOf("es", "en"), locator.languages)
    }

    @Test
    fun `does not copy again while the version marker matches`() {
        val target = File(tmp, "dictionaries")
        val source = assets()
        DictionaryInstaller(source, target).ensureInstalled()
        val opensAfterFirst = source.opens
        DictionaryInstaller(source, target).ensureInstalled()
        assertEquals(opensAfterFirst + 1, source.opens) // only the index is read again
    }

    @Test
    fun `refreshes when the data version changes`() {
        val target = File(tmp, "dictionaries")
        DictionaryInstaller(assets("1"), target).ensureInstalled()
        val updated = DictionaryInstaller(
            assets("2", "es-new".toByteArray()),
            target
        ).ensureInstalled()
        assertEquals("es-new", updated.fileFor(Locale.forLanguageTag("es"))!!.readText())
        assertEquals("2", File(target, DictionaryInstaller.MARKER_FILE).readText())
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
    fun `rejects corrupted assets and leaves nothing installed`() {
        val target = File(tmp, "dictionaries")
        val corrupt = assets(esSha = sha("other".toByteArray()))
        assertThrows(IOException::class.java) {
            DictionaryInstaller(corrupt, target).ensureInstalled()
        }
        assertFalse(target.exists())
        assertFalse(File(tmp, "dictionaries.staging").exists())
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
}
