// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.languages.LanguageCatalog
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanguagesModelTest {
    private val defaults = KeyboardSettings().sanitized()

    @Test
    fun `rows list the enabled languages first, in cycling order, then the rest`() {
        val rows = LanguagesModel.rows(
            KeyboardSettings(enabledLanguages = listOf("ru", "es")).sanitized()
        )
        assertEquals(LanguageCatalog.all.size, rows.size)
        assertEquals(listOf("ru", "es"), rows.take(2).map { it.language.tag })
        assertTrue(rows.take(2).all { it.enabled })
        assertTrue(rows.drop(2).none { it.enabled })
        assertEquals(
            LanguageCatalog.all.map { it.tag }.filter { it !in listOf("ru", "es") },
            rows.drop(2).map { it.language.tag }
        )
    }

    @Test
    fun `the last language cannot be switched off`() {
        val one = KeyboardSettings(enabledLanguages = listOf("fr")).sanitized()
        assertFalse(LanguagesModel.rows(one).first().canToggle)
        assertEquals(listOf("fr"), LanguagesModel.toggle(one, "fr", false).enabledLanguages)
        assertTrue(LanguagesModel.rows(defaults).first().canToggle)
    }

    @Test
    fun `enabling appends and disabling removes`() {
        val three = LanguagesModel.toggle(defaults, "fr", true)
        assertEquals(listOf("es", "en-US", "fr"), three.enabledLanguages)
        val back = LanguagesModel.toggle(three, "en-US", false)
        assertEquals(listOf("es", "fr"), back.enabledLanguages)
        assertEquals(three, LanguagesModel.toggle(three, "fr", true))
        assertEquals(three, LanguagesModel.toggle(three, "nope", true))
        assertEquals(three, LanguagesModel.toggle(three, "de", false))
    }

    @Test
    fun `disabling the language in use moves the keyboard to an enabled one`() {
        val fr =
            KeyboardSettings(enabledLanguages = listOf("es", "fr"), letterLayoutId = "fr_azerty")
        val off = LanguagesModel.toggle(fr.sanitized(), "fr", false)
        assertEquals(listOf("es"), off.enabledLanguages)
        assertEquals("es_qwerty", off.letterLayoutId)
    }

    @Test
    fun `at most six languages and the rest are locked`() {
        var s = defaults
        listOf("fr", "de", "it", "ru").forEach { s = LanguagesModel.toggle(s, it, true) }
        assertEquals(LanguageCatalog.MAX_ENABLED, s.enabledLanguages.size)
        val seventh = LanguagesModel.toggle(s, "el", true)
        assertEquals(s.enabledLanguages, seventh.enabledLanguages)
        val rows = LanguagesModel.rows(s)
        assertTrue(rows.filter { it.enabled }.all { it.canToggle })
        // Only the other variant of an enabled language can still be picked (it replaces the enabled one).
        assertEquals(
            listOf("en-GB"),
            rows.filter {
                !it.enabled && it.canToggle
            }.map { it.language.tag }
        )
    }

    @Test
    fun `a regional variant replaces its sibling and works at the limit`() {
        val gb = LanguagesModel.toggle(defaults, "en-GB", true)
        assertEquals(listOf("es", "en-GB"), gb.enabledLanguages)
        val row = LanguagesModel.rows(defaults).first { it.language.tag == "en-GB" }
        assertEquals("en-US", row.replaces?.tag)
        assertTrue(row.canToggle)
        assertNull(LanguagesModel.rows(defaults).first { it.language.tag == "fr" }.replaces)

        var full = defaults
        listOf("fr", "de", "it", "ru").forEach { full = LanguagesModel.toggle(full, it, true) }
        val swapped = LanguagesModel.toggle(full, "en-GB", true)
        assertEquals(listOf("es", "en-GB", "fr", "de", "it", "ru"), swapped.enabledLanguages)
    }

    @Test
    fun `only languages with several layouts offer a choice`() {
        val s = KeyboardSettings(
            enabledLanguages = listOf("es", "fr", "de", "en-US", "ru")
        ).sanitized()
        assertEquals(listOf("fr", "de", "en-US"), LanguagesModel.withLayoutChoice(s).map { it.tag })
    }

    @Test
    fun `choosing a layout for the language in use switches to it at once`() {
        val s = KeyboardSettings(
            enabledLanguages = listOf("fr", "de"),
            letterLayoutId = "fr_azerty"
        ).sanitized()
        val qwerty = LanguagesModel.chooseLayout(s, "fr", "fr_qwerty")
        assertEquals("fr_qwerty", qwerty.letterLayoutId)
        assertEquals("fr_qwerty", qwerty.layoutOf("fr"))
        val other = LanguagesModel.chooseLayout(s, "de", "de_qwerty")
        assertEquals("fr_azerty", other.letterLayoutId)
        assertEquals("de_qwerty", other.layoutOf("de"))
        assertEquals(s, LanguagesModel.chooseLayout(s, "fr", "de_qwertz"))
        assertEquals(s, LanguagesModel.chooseLayout(s, "xx", "fr_qwerty"))
        // Going back to the default layout leaves no override behind.
        val back = LanguagesModel.chooseLayout(qwerty, "fr", "fr_azerty")
        assertEquals(emptyMap<String, String>(), back.languageLayouts)
    }

    @Test
    fun `the other name appears only when it differs`() {
        val spanish = LanguageCatalog.find("es")!!
        assertNull(otherName(spanish, Locale.forLanguageTag("es")))
        assertEquals("Spanish", otherName(spanish, Locale.ENGLISH))
        val russian = LanguageCatalog.find("ru")!!
        assertEquals("Russian", otherName(russian, Locale.ENGLISH))
        assertEquals("ruso", otherName(russian, Locale.forLanguageTag("es"))?.lowercase())
    }
}
