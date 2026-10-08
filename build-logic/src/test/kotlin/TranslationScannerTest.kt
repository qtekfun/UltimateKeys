// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationScannerTest {
    private fun xml(body: String) = "<resources>$body</resources>"

    @Test
    fun `parses names and placeholders and skips untranslatable strings`() {
        val parsed = TranslationScanner.parse(
            xml(
                """<string name="a">Hello %1${'$'}s, %2${'$'}d items 100%%</string>
                   <string name="b" translatable="false">x</string>
                   <string name="c">Plain</string>"""
            )
        )
        assertEquals(listOf("a", "c"), parsed.keys.toList())
        assertEquals(listOf("%d", "%s"), parsed["a"])
        assertTrue(parsed.getValue("c").isEmpty())
    }

    @Test
    fun `reports missing extra and mismatched placeholders`() {
        val base = TranslationScanner.parse(xml("""<string name="a">%s</string><string name="b">B</string>"""))
        val es = TranslationScanner.parse(xml("""<string name="a">%d</string><string name="z">Z</string>"""))
        val problems = TranslationScanner.compare("app", "es", base, es).associate { it.name to it.message }
        assertEquals(setOf("a", "b", "z"), problems.keys)
        assertTrue(problems.getValue("b").contains("missing"))
        assertTrue(problems.getValue("a").contains("placeholders"))
        assertTrue(problems.getValue("z").contains("absent"))
    }

    @Test
    fun `plural items are compared one by one`() {
        val base = TranslationScanner.parse(
            xml("""<plurals name="p"><item quantity="one">1 %d</item><item quantity="other">%d</item></plurals>""")
        )
        assertEquals(setOf("p[0]", "p[1]"), base.keys)
    }

    @Test
    fun `scans module resource folders`() {
        val root = Files.createTempDirectory("tr").toFile()
        try {
            val res = File(root, "m/src/main/res").also { File(it, "values").mkdirs() }
            File(res, "values/strings.xml").writeText(xml("""<string name="a">A</string>"""))
            assertEquals(1, TranslationScanner.scan(root).size)
            File(res, "values-es").mkdirs()
            File(res, "values-es/strings.xml").writeText(xml("""<string name="a">Á</string>"""))
            assertTrue(TranslationScanner.scan(root).isEmpty())
            File(res, "values-fr").mkdirs()
            File(res, "values-fr/strings.xml").writeText(xml("""<string name="a">À</string>"""))
            assertEquals(1, TranslationScanner.scan(root).size)
        } finally {
            root.deleteRecursively()
        }
    }
}
