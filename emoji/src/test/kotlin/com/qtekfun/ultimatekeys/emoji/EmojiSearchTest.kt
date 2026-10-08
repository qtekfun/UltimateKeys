// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.emoji

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmojiSearchTest {
    private fun index(vararg rows: Triple<String, String, List<String>>) =
        EmojiSearchIndex(rows.map { EmojiAnnotation(it.first, it.second, it.third) })

    @Test
    fun `normalization drops accents and case`() {
        assertEquals("corazon", SearchText.normalize("Corazón"))
        assertEquals("nino", SearchText.normalize("NIÑO"))
        assertEquals(listOf("cara", "feliz"), SearchText.words("Cara, feliz!"))
    }

    @Test
    fun `prefix and accent insensitive match`() {
        val idx = index(Triple("A", "corazón rojo", listOf("amor")))
        assertEquals(listOf("A"), idx.search("coraz").map { it.emoji })
        assertEquals(listOf("A"), idx.search("CORAZON").map { it.emoji })
        assertEquals(listOf("A"), idx.search("cor").map { it.emoji })
        assertTrue(idx.search("razon").isEmpty())
    }

    @Test
    fun `every word of the query must match`() {
        val idx = index(
            Triple("A", "red heart", listOf("love")),
            Triple("B", "red apple", listOf("fruit"))
        )
        assertEquals(listOf("A"), idx.search("red hea").map { it.emoji })
        assertTrue(idx.search("red banana").isEmpty())
        assertTrue(idx.search("   ").isEmpty())
    }

    @Test
    fun `a name beats a keyword and a whole word beats a prefix`() {
        val idx = index(
            Triple("kw", "alpha", listOf("cat")),
            Triple("pre", "category", emptyList()),
            Triple("name", "cat", emptyList())
        )
        assertEquals(listOf("name", "pre", "kw"), idx.search("cat").map { it.emoji })
    }

    @Test
    fun `the first name word and a shorter name rank higher and ties keep panel order`() {
        val idx = index(
            Triple("late", "big red cat", emptyList()),
            Triple("first", "cat big red", emptyList()),
            Triple("t1", "dog", emptyList()),
            Triple("t2", "dog", emptyList())
        )
        assertEquals(listOf("first", "late"), idx.search("cat").map { it.emoji })
        assertEquals(listOf("t1", "t2"), idx.search("dog").map { it.emoji })
    }

    @Test
    fun `the limit caps the results`() {
        val idx = EmojiSearchIndex((1..10).map { EmojiAnnotation("e$it", "face $it", emptyList()) })
        assertEquals(3, idx.search("face", limit = 3).size)
        assertEquals(10, idx.size)
    }

    @Test
    fun `parse reads the generated tsv format`() {
        val idx = EmojiSearchIndex.parse("# header\n😀\tgrinning face\tgrin|smile\n\n")
        assertEquals(1, idx.size)
        assertEquals("grinning face", idx.search("smi").single().name)
    }

    @Test
    fun `merged search keeps the best score per emoji`() {
        val en = index(Triple("X", "heart", emptyList()))
        val es =
            index(Triple("X", "corazón", listOf("heart")), Triple("Y", "corazón azul", emptyList()))
        val hits = EmojiSearch(listOf(en, es)).search("heart")
        assertEquals(listOf("X"), hits.map { it.emoji })
        assertEquals("heart", hits.single().name)
        assertEquals(
            listOf("X", "Y"),
            EmojiSearch(listOf(en, es)).search("cora").map {
                it.emoji
            }.sorted()
        )
    }

    // Real data: the generated CLDR index for both languages.

    private val data: EmojiData by lazy {
        val dir = File(checkNotNull(System.getProperty("emoji.assets")))
        EmojiData.load({ File(dir, it).inputStream() })
    }

    private fun top(query: String, count: Int = 5) = data.search.search(query, count).map {
        it.emoji
    }

    @Test
    fun `english names rank first`() {
        assertEquals("🍕", top("pizza").first())
        assertEquals("🚀", top("rocket").first())
        assertTrue("🐱" in top("cat face", 10), "cat face finds the cat face")
        assertTrue("❤️" in top("red heart", 3))
        assertTrue(top("smil", 20).isNotEmpty())
    }

    @Test
    fun `spanish names rank first and accents are optional`() {
        val heart = top("corazon rojo", 3)
        assertEquals(top("corazón rojo", 3), heart)
        assertTrue("❤️" in heart)
        assertTrue(top("gato", 10).any { it == "🐱" || it == "🐈" })
        assertTrue("🇪🇸" in top("espana", 5), "flag of Spain by name without the tilde")
        assertTrue("🇪🇸" in top("España", 5))
        assertTrue("🍕" in top("pizza", 3))
    }

    @Test
    fun `both languages answer one query`() {
        // "flag" is English and "bandera" Spanish; both find the flag of Spain.
        assertTrue("🇪🇸" in data.search.search("flag spain", 10).map { it.emoji })
        assertTrue("🇪🇸" in data.search.search("bandera espa", 10).map { it.emoji })
    }
}
