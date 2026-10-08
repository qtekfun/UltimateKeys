// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.languages

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanguageCatalogTest {
    @Test
    fun `tags are unique, well formed and resolve to a locale`() {
        val tags = LanguageCatalog.all.map { it.tag }
        assertEquals(tags.size, tags.toSet().size)
        LanguageCatalog.all.forEach {
            assertEquals(it.code, it.locale.language, it.tag)
            assertTrue(
                it.nativeName.isNotBlank() && it.englishName.isNotBlank() &&
                    it.spaceName.isNotBlank()
            )
        }
    }

    @Test
    fun `defaults are spanish and english and exist`() {
        assertEquals(listOf("es", "en-US"), LanguageCatalog.DEFAULT_ENABLED)
        LanguageCatalog.DEFAULT_ENABLED.forEach { assertNotNull(LanguageCatalog.find(it)) }
        assertNull(LanguageCatalog.find("xx"))
    }

    @Test
    fun `a language needs a layout`() {
        assertThrows(IllegalArgumentException::class.java) {
            Language("xx", "X", "X", Script.LATIN, emptyList())
        }
    }

    @Test
    fun `layout lookup prefers the enabled variant`() {
        assertEquals("en-US", LanguageCatalog.languageOfLayout("en_qwerty")?.tag)
        assertEquals(
            "en-GB",
            LanguageCatalog.languageOfLayout("en_qwerty", listOf("es", "en-GB"))?.tag
        )
        assertEquals("fr", LanguageCatalog.languageOfLayout("fr_qwerty")?.tag)
        assertNull(LanguageCatalog.languageOfLayout("nope"))
    }

    @Test
    fun `layoutFor honours a valid choice only`() {
        assertEquals("fr_azerty", LanguageCatalog.layoutFor("fr"))
        assertEquals("fr_qwerty", LanguageCatalog.layoutFor("fr", mapOf("fr" to "fr_qwerty")))
        assertEquals("fr_azerty", LanguageCatalog.layoutFor("fr", mapOf("fr" to "de_qwertz")))
        assertEquals("es_qwerty", LanguageCatalog.layoutFor("unknown"))
    }

    @Test
    fun `enabled list is cleaned`() {
        assertEquals(
            listOf("fr", "de"),
            LanguageCatalog.sanitizeEnabled(listOf("fr", "xx", "fr", "de"))
        )
        assertEquals(
            listOf("en-GB", "fr"),
            LanguageCatalog.sanitizeEnabled(listOf("en-GB", "en-US", "fr"))
        )
        assertEquals(LanguageCatalog.DEFAULT_ENABLED, LanguageCatalog.sanitizeEnabled(emptyList()))
        assertEquals(LanguageCatalog.DEFAULT_ENABLED, LanguageCatalog.sanitizeEnabled(listOf("zz")))
        val many = LanguageCatalog.all.map { it.tag }
        assertEquals(LanguageCatalog.MAX_ENABLED, LanguageCatalog.sanitizeEnabled(many).size)
    }

    @Test
    fun `layout choices are cleaned`() {
        val cleaned = LanguageCatalog.sanitizeLayouts(
            mapOf(
                "fr" to "fr_qwerty",
                "de" to "fr_qwerty",
                "es" to "es_qwerty",
                "it" to "it_qwerty",
                "xx" to "x",
                "ru" to "ru_jcuken"
            ),
            enabled = listOf("fr", "de", "es", "xx")
        )
        assertEquals(mapOf("fr" to "fr_qwerty"), cleaned)
    }

    @Test
    fun `layout ids list has no repeats`() {
        assertEquals(
            LanguageCatalog.letterLayoutIds.size,
            LanguageCatalog.letterLayoutIds.toSet().size
        )
    }
}
