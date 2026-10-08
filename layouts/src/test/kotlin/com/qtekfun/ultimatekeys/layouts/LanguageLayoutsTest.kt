// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.layouts

import com.qtekfun.ultimatekeys.languages.LanguageCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Checks every letter layout of the language catalog, so a new language cannot ship a broken one. */
class LanguageLayoutsTest {
    private val ids = LanguageCatalog.letterLayoutIds

    private fun charKeys(id: String) =
        LayoutRepository.load(id).rows.flatMap { it.keys }.filterIsInstance<CharKey>()

    /** Characters a language needs that must be reachable by a key or a long press of its default layout. */
    private val required = mapOf(
        "es" to "áéíóúüñ",
        "en-US" to "abcdefghijklmnopqrstuvwxyz",
        "en-GB" to "abcdefghijklmnopqrstuvwxyz",
        "fr" to "àâæçéèêëîïôœùûüÿ",
        "de" to "äöüß",
        "it" to "àèéìòù",
        "pt-BR" to "áâãàçéêíóôõú",
        "pt-PT" to "áâãàçéêíóôõú",
        "nl" to "éëïó",
        "pl" to "ąćęłńóśźż",
        "cs" to "áčďéěíňóřšťúůýž",
        "da" to "æøåé",
        "nb" to "æøåé",
        "sv" to "åäöé",
        "fi" to "åäöšž",
        "tr" to "çğıöşüâî",
        "ro" to "ăâîșț",
        "hr" to "čćđšž",
        "sl" to "čšž",
        "sr-Cyrl" to "абвгдђежзијклљмнњопрстћуфхцчџш",
        "lt" to "ąčęėįšųūž",
        "lv" to "āčēģīķļņšūž",
        "ru" to "абвгдеёжзийклмнопрстуфхцчшщъыьэюя",
        "el" to "αβγδεζηθικλμνξοπρσςτυφχψωάέήίόύώ"
    )

    @Test
    fun `every catalog layout exists and parses`() {
        ids.forEach { assertEquals(it, LayoutRepository.load(it).id) }
    }

    @Test
    fun `layouts have three letter rows and a bottom row with space and enter`() {
        ids.forEach { id ->
            val layout = LayoutRepository.load(id)
            assertEquals(4, layout.rows.size, id)
            val actions = layout.rows.last().keys.filterIsInstance<ActionKey>().map { it.action }
            assertTrue(KeyAction.SPACE in actions && KeyAction.ENTER in actions, id)
            val all = layout.rows.flatMap {
                it.keys
            }.filterIsInstance<ActionKey>().map { it.action }
            assertTrue(KeyAction.SHIFT in all && KeyAction.DELETE in all, id)
        }
    }

    @Test
    fun `the layout locale is the language of the layout`() {
        ids.forEach { id ->
            val owner = LanguageCatalog.languageOfLayout(id)!!
            val locale = LayoutRepository.load(id).locale!!
            assertEquals(owner.code, locale.substringBefore('-'), id)
        }
    }

    @Test
    fun `keys are printable single letters without repeats`() {
        ids.forEach { id ->
            val letters = LayoutRepository.load(id).rows.dropLast(1).flatMap { it.keys }
                .filterIsInstance<CharKey>()
            assertTrue(letters.size in 26..33, "$id has ${letters.size} letter keys")
            letters.forEach { key ->
                assertTrue(key.label.isNotBlank(), id)
                assertFalse(key.label.any { Character.isISOControl(it) || it.isWhitespace() }, id)
                assertEquals(1, key.label.codePointCount(0, key.label.length), "$id: ${key.label}")
            }
            assertEquals(
                letters.size,
                letters.map {
                    it.label
                }.toSet().size,
                "$id has a repeated key"
            )
        }
    }

    @Test
    fun `alternatives are printable, distinct and never the key itself`() {
        ids.forEach { id ->
            charKeys(id).forEach { key ->
                assertEquals(
                    key.alternatives.size,
                    key.alternatives.toSet().size,
                    "$id ${key.label}"
                )
                assertFalse(key.label in key.alternatives, "$id ${key.label}")
                key.alternatives.forEach { alt ->
                    assertTrue(alt.isNotBlank() && alt.none { Character.isISOControl(it) }, id)
                }
            }
        }
    }

    @Test
    fun `the top row carries the digits as hints in order`() {
        ids.forEach { id ->
            val top = LayoutRepository.load(id).rows.first().keys.filterIsInstance<CharKey>()
            "1234567890".forEachIndexed { index, digit ->
                assertEquals(digit.toString(), top[index].hint, "$id key $index")
            }
            // The digits stay out of the other rows, whose first alternative must not look like a hint.
            LayoutRepository.load(id).rows.drop(1).flatMap { it.keys }.filterIsInstance<CharKey>()
                .forEach { assertEquals(null, it.hint, "$id ${it.label}") }
        }
    }

    @Test
    fun `row widths are consistent`() {
        ids.forEach { id ->
            LayoutRepository.load(id).rows.forEach { row ->
                val total = row.keys.sumOf { it.width.toDouble() }
                assertTrue(total in 9.0..12.5, "$id row width $total")
                row.keys.filterIsInstance<ActionKey>().filter {
                    it.action == KeyAction.SHIFT || it.action == KeyAction.DELETE
                }.forEach { assertTrue(it.width in 1.0f..2.1f, id) }
            }
        }
    }

    @Test
    fun `every character of a language is reachable on its layouts`() {
        for (language in LanguageCatalog.all) {
            for (id in language.layoutIds) {
                // Only the default layout has to type every special character; variants need the base alphabet.
                val needed = required.getValue(language.tag).filter {
                    id == language.defaultLayoutId || it.code < ASCII
                }
                val lacking = needed.filter { it.toString() !in reachable(id) }
                assertTrue(lacking.isEmpty(), "${language.tag}/$id lacks '$lacking'")
            }
        }
    }

    private fun reachable(id: String): Set<String> =
        charKeys(id).flatMap { listOf(it.output) + it.alternatives }.toSet()

    @Test
    fun `the number row variant works for every layout`() {
        ids.forEach { id ->
            val plain = LayoutRepository.pages(id, numberRow = false).letters
            val withRow = LayoutRepository.pages(id, numberRow = true).letters
            assertEquals(plain.rows.size + 1, withRow.rows.size, id)
            withRow.rows.drop(1).flatMap { it.keys }.filterIsInstance<CharKey>().forEach {
                assertTrue(
                    it.hint == null && it.alternatives.none { a ->
                        a.length == 1 && a[0].isDigit()
                    },
                    id
                )
            }
        }
    }

    @Test
    fun `letter rows feed the engine geometry in the layout script`() {
        val russian = LayoutRepository.load("ru_jcuken").letterRows()
        assertEquals(listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю"), russian)
        assertEquals(
            listOf("azertyuiop", "qsdfghjklm", "wxcvbn"),
            LayoutRepository.load("fr_azerty").letterRows()
        )
        assertTrue(LayoutRepository.load("tr_q").letterRows().joinToString("").contains("ı"))
        ids.forEach { id ->
            val rows = LayoutRepository.load(id).letterRows()
            assertEquals(3, rows.size, id)
            assertTrue(
                rows.all { r ->
                    r.all { Character.isLetter(it) && it == it.lowercaseChar() }
                },
                id
            )
        }
    }

    @Test
    fun `typed text of shifted keys follows the language case rules`() {
        val tr = java.util.Locale.forLanguageTag("tr")
        assertEquals("İ", "i".uppercase(tr))
        assertEquals("I", "ı".uppercase(tr))
    }

    private companion object {
        const val ASCII = 128
    }
}
