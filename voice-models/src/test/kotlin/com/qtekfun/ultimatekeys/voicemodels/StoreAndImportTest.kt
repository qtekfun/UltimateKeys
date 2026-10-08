// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voicemodels

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class StoreAndImportTest {
    @TempDir
    lateinit var root: File

    private val bytes = fakeModelBytes(4096)
    private val spec = specFor(bytes)
    private val catalog = catalogOf(spec)

    @Test
    fun `sha256 of known input`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Checksums.sha256(ByteArrayInputStream("abc".toByteArray()))
        )
        val file = File(root, "f").apply { writeText("abc") }
        assertEquals(
            Checksums.sha256(ByteArrayInputStream("abc".toByteArray())),
            Checksums.sha256(file)
        )
    }

    @Test
    fun `install verifies and moves into place`() {
        val store = storeIn(root, catalog)
        val seen = mutableListOf<Long>()
        val file = store.install(spec, ByteArrayInputStream(bytes)) { seen += it }
        assertEquals(File(root, "models/${spec.file}"), file)
        assertTrue(store.isInstalled(spec))
        assertEquals(bytes.size.toLong(), seen.last())
        assertEquals(listOf("base"), store.installed().map { it.id })
        assertEquals(spec, store.installed().single().spec)
        assertFalse(store.stagingFile("${spec.file}.install").exists())
    }

    @Test
    fun `a wrong checksum installs nothing and cleans up`() {
        val store = storeIn(root, catalog)
        val corrupt = bytes.copyOf().also { it[100] = 7 }
        val error =
            assertThrows<ChecksumMismatchException> {
                store.install(spec, ByteArrayInputStream(corrupt))
            }
        assertEquals(spec.sha256, error.expected)
        assertFalse(store.isInstalled(spec))
        assertTrue(store.installed().isEmpty())
        assertFalse(store.stagingFile("${spec.file}.install").exists())
    }

    @Test
    fun `a broken stream installs nothing`() {
        val store = storeIn(root, catalog)
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("gone")
        }
        assertThrows<IOException> { store.install(spec, broken) }
        assertFalse(store.isInstalled(spec))
    }

    @Test
    fun `commit hashes a staged file and deletes it when wrong`() {
        val store = storeIn(root, catalog)
        val good = store.stagingFile("good").apply { writeBytes(bytes) }
        store.commit(spec, good)
        assertTrue(store.isInstalled(spec))
        val bad = store.stagingFile("bad").apply { writeBytes(bytes.copyOf(10)) }
        assertThrows<ChecksumMismatchException> { store.commit(spec, bad) }
        assertFalse(bad.exists())
    }

    @Test
    fun `listing puts catalog models first and ignores wrong sized or partial files`() {
        val store = storeIn(root, catalog)
        File(root, "models").mkdirs()
        File(root, "models/zz-custom.bin").writeBytes(ByteArray(3))
        File(root, "models/aa-custom.bin").writeBytes(ByteArray(2))
        File(root, "models/${spec.file}").writeBytes(ByteArray(5)) // truncated: not installed
        File(root, "models/notes.txt").writeText("x")
        assertEquals(listOf("aa-custom", "zz-custom"), store.installed().map { it.id })
        assertFalse(store.isInstalled(spec))
        store.install(spec, ByteArrayInputStream(bytes))
        val models = store.installed()
        assertEquals(listOf("base", "aa-custom", "zz-custom"), models.map { it.id })
        assertNull(models[1].spec)
        assertTrue(store.delete(models[0]))
        assertFalse(store.isInstalled(spec))
    }

    @Test
    fun `cleaning staging keeps resumable downloads`() {
        val store = storeIn(root, catalog)
        store.stagingFile("a.install").writeText("x")
        store.stagingFile("picked.import").writeText("x")
        store.partialFile(spec).writeText("x")
        store.cleanStaging()
        assertFalse(store.stagingFile("a.install").exists())
        assertFalse(store.stagingFile("picked.import").exists())
        assertTrue(store.partialFile(spec).exists())
        store.deletePartial(spec)
        assertFalse(store.partialFile(spec).exists())
        assertTrue(store.freeBytes() > 0)
    }

    // --- import ---

    private fun importer(store: ModelStore) = ModelImporter(store, catalog, minBytes = 1000)

    @Test
    fun `a catalog model is recognised by its hash whatever the file is called`() {
        val store = storeIn(root, catalog)
        val importer = importer(store)
        val inspection = importer.inspect(ByteArrayInputStream(bytes))
        assertTrue(inspection is ImportInspection.Recognized)
        val model = importer.accept(inspection)!!
        assertEquals("base", model.id)
        assertEquals(spec, model.spec)
        assertTrue(store.isInstalled(spec))
    }

    @Test
    fun `an unknown whisper file waits for confirmation and is installed under its hash`() {
        val store = storeIn(root, catalog)
        val importer = importer(store)
        val other = fakeModelBytes(5000, seed = 9)
        val inspection = importer.inspect(ByteArrayInputStream(other))
        inspection as ImportInspection.Unrecognized
        assertEquals(sha256Of(other), inspection.sha256)
        assertEquals(5000L, inspection.bytes)
        assertTrue(store.installed().isEmpty())
        val model = importer.accept(inspection)!!
        assertEquals("imported-${sha256Of(other).take(12)}", model.id)
        assertNull(model.spec)
        assertEquals(listOf(model.id), store.installed().map { it.id })
    }

    @Test
    fun `discarding removes the staged copy`() {
        val store = storeIn(root, catalog)
        val importer = importer(store)
        val inspection = importer.inspect(ByteArrayInputStream(fakeModelBytes(5000, seed = 3)))
        inspection as ImportInspection.Unrecognized
        assertTrue(inspection.staged.exists())
        importer.discard(inspection)
        assertFalse(inspection.staged.exists())
        val recognised = importer.inspect(ByteArrayInputStream(bytes))
        importer.discard(recognised)
        importer.discard(ImportInspection.Rejected(ImportRejection.NOT_GGML))
        assertNull(importer.accept(ImportInspection.Rejected(ImportRejection.NOT_GGML)))
    }

    @Test
    fun `files that are not whisper models are rejected before anything is kept`() {
        val store = storeIn(root, catalog)
        val importer = importer(store)
        fun reason(data: ByteArray) =
            (importer.inspect(ByteArrayInputStream(data)) as ImportInspection.Rejected).reason

        assertEquals(ImportRejection.NOT_GGML, reason(ByteArray(5000) { 7 }))
        assertEquals(ImportRejection.TOO_SMALL, reason(ByteArray(3)))
        assertEquals(ImportRejection.TOO_SMALL, reason(ByteArray(0)))
        assertEquals(ImportRejection.TOO_SMALL, reason(fakeModelBytes(500)))
        val llama = fakeModelBytes(5000).also {
            it[4] = 1
            it[5] = 0
        } // vocabulary 1
        assertEquals(ImportRejection.NOT_WHISPER, reason(llama))
        assertFalse(store.stagingFile("picked.import").exists())
        assertTrue(store.installed().isEmpty())
    }

    @Test
    fun `an oversized stream is cut off`() {
        val store = storeIn(root, catalog)
        val tiny = ModelImporter(store, catalog, minBytes = 1, maxBytes = 100_000)
        assertTrue(GgmlHeader.isWhisper(fakeModelBytes(64)))
        assertFalse(GgmlHeader.isWhisper(fakeModelBytes(64).copyOf(8)))
        assertFalse(GgmlHeader.isGgml(ByteArray(2)))
        val endless = object : InputStream() {
            private val head = fakeModelBytes(12)
            private var pos = 0L
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                for (i in 0 until len) {
                    b[off + i] =
                        if (pos + i < head.size) head[(pos + i).toInt()] else 1
                }
                pos += len
                return len
            }
        }
        val result = tiny.inspect(endless)
        assertEquals(ImportRejection.TOO_LARGE, (result as ImportInspection.Rejected).reason)
        assertFalse(store.stagingFile("picked.import").exists())
    }

    @Test
    fun `a read error during import is thrown and leaves nothing staged`() {
        val store = storeIn(root, catalog)
        val head = fakeModelBytes(64)
        val flaky = object : InputStream() {
            private var sent = false
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (sent) throw IOException("card removed")
                sent = true
                head.copyInto(b, off, 0, minOf(len, head.size))
                return minOf(len, head.size)
            }
        }
        assertThrows<IOException> { ModelImporter(store, catalog, minBytes = 1).inspect(flaky) }
        assertFalse(store.stagingFile("picked.import").exists())
    }
}
