// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanguageSettingsTest {
    private fun withLayout(languages: List<String>, layout: String) =
        KeyboardSettings(enabledLanguages = languages, letterLayoutId = layout).sanitized()

    @Test
    fun `the defaults are spanish and english as before`() {
        val s = KeyboardSettings().sanitized()
        assertEquals(listOf("es", "en-US"), s.enabledLanguages)
        assertEquals("es_qwerty", s.letterLayoutId)
        assertEquals("es", s.activeLanguage.tag)
        assertEquals("en-US", s.nextLanguage())
        assertTrue(s.showLanguageOnSpace)
    }

    @Test
    fun `enabled languages are cleaned`() {
        val s = KeyboardSettings(
            enabledLanguages =
                listOf("fr", "xx", "fr", "de", "en-GB", "en-US", "it", "ru", "el", "tr")
        ).sanitized()
        assertEquals(listOf("fr", "de", "en-GB", "it", "ru", "el"), s.enabledLanguages)
        val empty = KeyboardSettings(enabledLanguages = emptyList()).sanitized()
        assertEquals(listOf("es", "en-US"), empty.enabledLanguages)
    }

    @Test
    fun `the active layout always belongs to an enabled language`() {
        val s = withLayout(listOf("fr", "de"), "es_qwerty")
        assertEquals("fr_azerty", s.letterLayoutId)
        val kept = withLayout(listOf("fr", "de"), "de_qwertz")
        assertEquals("de_qwertz", kept.letterLayoutId)
        assertEquals("de", kept.activeLanguage.tag)
        val unknown = KeyboardSettings(letterLayoutId = "klingon").sanitized()
        assertEquals("es_qwerty", unknown.letterLayoutId)
    }

    @Test
    fun `a shared english layout belongs to the enabled variant`() {
        val s = withLayout(listOf("es", "en-GB"), "en_qwerty")
        assertEquals("en-GB", s.activeLanguage.tag)
        assertEquals("es", s.nextLanguage())
    }

    @Test
    fun `the language key cycles through the enabled languages and wraps`() {
        var s = KeyboardSettings(enabledLanguages = listOf("es", "fr", "ru")).sanitized()
        val seen = mutableListOf(s.activeLanguage.tag)
        repeat(3) {
            s = s.copy(letterLayoutId = s.layoutOf(s.nextLanguage())).sanitized()
            seen += s.activeLanguage.tag
        }
        assertEquals(listOf("es", "fr", "ru", "es"), seen)
        val alone = KeyboardSettings(enabledLanguages = listOf("de")).sanitized()
        assertEquals("de", alone.nextLanguage())
    }

    @Test
    fun `layout choices are cleaned and used`() {
        val s = KeyboardSettings(
            enabledLanguages = listOf("es", "fr"),
            languageLayouts = mapOf(
                "fr" to "fr_qwerty",
                "es" to "fr_azerty",
                "de" to "de_qwerty",
                "fr2" to "x"
            )
        ).sanitized()
        assertEquals(mapOf("fr" to "fr_qwerty"), s.languageLayouts)
        assertEquals("fr_qwerty", s.layoutOf("fr"))
        assertEquals("es_qwerty", s.layoutOf("es"))
    }

    @Test
    fun `the codec round trips and ignores junk`() {
        val list = listOf("a", "b")
        assertEquals(list, LanguageSettingsCodec.decodeList(LanguageSettingsCodec.encodeList(list)))
        assertEquals(emptyList<String>(), LanguageSettingsCodec.decodeList(""))
        val map = mapOf("fr" to "fr_qwerty", "de" to "de_qwerty")
        assertEquals(map, LanguageSettingsCodec.decodeMap(LanguageSettingsCodec.encodeMap(map)))
        assertEquals(mapOf("a" to "b"), LanguageSettingsCodec.decodeMap("a=b =x c= junk"))
    }

    @Test
    fun `datastore round trips languages and sanitises them`() = runTest {
        val file = Files.createTempFile("settings", ".preferences_pb").toFile().apply { delete() }
        val repo = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        )
        assertEquals(KeyboardSettings(), repo.settings.first())
        repo.update {
            it.copy(
                enabledLanguages = listOf("fr", "de", "ru"),
                languageLayouts = mapOf("fr" to "fr_qwerty"),
                letterLayoutId = "fr_qwerty",
                showLanguageOnSpace = false
            )
        }
        val s = repo.settings.first()
        assertEquals(listOf("fr", "de", "ru"), s.enabledLanguages)
        assertEquals(mapOf("fr" to "fr_qwerty"), s.languageLayouts)
        assertEquals("fr_qwerty", s.letterLayoutId)
        assertEquals(false, s.showLanguageOnSpace)

        repo.update { it.copy(enabledLanguages = emptyList(), letterLayoutId = "ru_jcuken") }
        val reset = repo.settings.first()
        assertEquals(listOf("es", "en-US"), reset.enabledLanguages)
        assertEquals("es_qwerty", reset.letterLayoutId)
        assertEquals(emptyMap<String, String>(), reset.languageLayouts)
    }

    @Test
    fun `fake repository sanitises languages too`() = runTest {
        val repo = FakeSettingsRepository()
        repo.update { it.copy(enabledLanguages = listOf("nope")) }
        assertEquals(listOf("es", "en-US"), repo.settings.first().enabledLanguages)
    }
}
