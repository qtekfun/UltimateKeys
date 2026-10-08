// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OriginalityScannerTest {
    @Test
    fun `finds whole words regardless of case and reports the line`() {
        val hits = OriginalityScanner.scanText("a.kt", "fine\nlike GBoard here\nand iOS too")
        assertEquals(listOf(2 to "gboard", 3 to "ios"), hits.map { it.line to it.term })
    }

    @Test
    fun `does not trip on words that merely contain a term`() {
        val text = "studios, bios, pineapple, apples, kikai"
        assertTrue(OriginalityScanner.scanText("a.txt", text).isEmpty())
    }

    @Test
    fun `scans text files and skips the spec documents and build output`() {
        val root = Files.createTempDirectory("orig").toFile()
        File(root, "SPEC.md").writeText("Gboard")
        File(root, "src").mkdirs()
        File(root, "src/Names.kt").writeText("val x = \"SwiftKey\"")
        File(root, "build").mkdirs()
        File(root, "build/Gen.kt").writeText("Samsung")
        File(root, "src/font.ttf").writeText("Samsung")
        val hits = OriginalityScanner.scan(root)
        assertEquals(listOf("src${File.separator}Names.kt"), hits.map { it.path })
        root.deleteRecursively()
    }
}
