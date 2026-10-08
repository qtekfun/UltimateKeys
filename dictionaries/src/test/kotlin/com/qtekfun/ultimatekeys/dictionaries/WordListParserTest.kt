// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WordListParserTest {
    private val sample = """
        dictionary=main:xx,locale=xx,description=Sample,date=1,version=7
         word=de,f=225,flags=,originalFreq=225
         word=km,f=159,flags=abbreviation,originalFreq=159
         word=podó,f=150,flags=hand-added,originalFreq=150
         word=perro,f=126,flags=,originalFreq=126,possibly_offensive=true
          shortcut=ignored,f=whitelist
          bigram=ignored,f=10
         word=amd,f=0,not_a_word=true
         word=damn,f=90,flags=nonword:offensive,originalFreq=90
    """.trimIndent()

    @Test
    fun `parses header and word entries`() {
        val list = WordListParser.parse(sample.byteInputStream())
        assertEquals("xx", list.header.locale)
        assertEquals("7", list.header.version)
        assertEquals(listOf("de", "km", "podó", "perro", "amd", "damn"), list.words.map { it.word })
        assertEquals(225, list.words[0].frequency)
        assertEquals(setOf("abbreviation"), list.words[1].flags)
    }

    @Test
    fun `reads offensive and not-a-word markers and multi-valued flags`() {
        val words = WordListParser.parse(sample.byteInputStream()).words.associateBy { it.word }
        assertTrue(words.getValue("perro").possiblyOffensive)
        assertFalse(words.getValue("de").possiblyOffensive)
        assertTrue(words.getValue("amd").notAWord)
        assertEquals(0, words.getValue("amd").frequency)
        assertEquals(setOf("nonword", "offensive"), words.getValue("damn").flags)
    }

    @Test
    fun `parses gzip streams`() {
        val bytes = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(sample.toByteArray()) }
        }.toByteArray()
        assertEquals(6, WordListParser.parseGzip(bytes.inputStream()).words.size)
    }

    @Test
    fun `rejects lists without header and malformed word lines`() {
        assertThrows(IllegalArgumentException::class.java) {
            WordListParser.parse(" word=a,f=1,flags=".byteInputStream())
        }
        assertThrows(IllegalArgumentException::class.java) { WordListParser.parseWord(" word=a") }
        assertThrows(IllegalArgumentException::class.java) {
            WordListParser.parseWord(" word=a,f=x")
        }
    }

    @Test
    fun `a headword may contain a comma`() {
        assertEquals("a,b", WordListParser.parseWord(" word=a,b,f=3,flags=").word)
    }
}
