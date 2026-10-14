// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.qtekfun.ultimatekeys.style.Presets
import com.qtekfun.ultimatekeys.style.Style
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupCodecTest {
    private fun file(vararg entries: BackupEntry, words: List<BackupWord> = emptyList()) =
        BackupFile(appVersion = "1.0.0", preferences = entries.toList(), userWords = words)

    private fun loaded(text: String) = (BackupCodec.decode(text) as BackupLoadResult.Loaded).file

    private fun error(text: String) = (BackupCodec.decode(text) as BackupLoadResult.Failed).error

    @Test
    fun `a backup survives a round trip`() {
        val original = file(
            BackupEntry("height_percent", "int", JsonPrimitive(110)),
            BackupEntry("number_row", "boolean", JsonPrimitive(true)),
            BackupEntry("active_style_id", "string", JsonPrimitive("soft")),
            BackupEntry(
                "tags",
                "string_set",
                JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b")))
            ),
            words = listOf(BackupWord("es", "hola"))
        )
        assertEquals(original, loaded(BackupCodec.encode(original)))
    }

    @Test
    fun `unknown keys are ignored so a newer file with extra fields still reads`() {
        val text = BackupCodec.encode(file()).replaceFirst("{", "{\"future\": 1,")
        assertEquals(0, loaded(text).preferences.size)
    }

    @Test
    fun `things that are not backups are told apart`() {
        assertEquals(BackupError.NotJson, error("this is not json"))
        assertEquals(BackupError.NotABackup, error("[1, 2, 3]"))
        assertEquals(BackupError.NotABackup, error("{\"format\": \"something-else\"}"))
        assertEquals(
            BackupError.TooLarge,
            error(" ".repeat(BackupCodec.MAX_BYTES + 1))
        )
    }

    @Test
    fun `a newer schema is refused`() {
        val text = BackupCodec.encode(
            file()
        ).replace("\"schemaVersion\": 1", "\"schemaVersion\": 9")
        assertEquals(BackupError.NewerVersion(9), error(text))
    }

    @Test
    fun `entries with a bad name, type or value are refused`() {
        val badName = BackupEntry("Bad Name!", "int", JsonPrimitive(1))
        val badType = BackupEntry("ok_name", "banana", JsonPrimitive(1))
        val mismatch = BackupEntry("ok_name", "int", JsonPrimitive("text"))
        val repeated = listOf(
            BackupEntry("same", "int", JsonPrimitive(1)),
            BackupEntry("same", "int", JsonPrimitive(2))
        )
        listOf(listOf(badName), listOf(badType), listOf(mismatch), repeated).forEach {
            val result = BackupCodec.decode(BackupCodec.encode(BackupFile(preferences = it)))
            assertInstanceOf(
                BackupError.Invalid::class.java,
                (result as BackupLoadResult.Failed).error
            )
        }
    }

    @Test
    fun `too many entries or words are refused`() {
        val entries =
            List(BackupCodec.MAX_ENTRIES + 1) { BackupEntry("k$it", "int", JsonPrimitive(it)) }
        assertInstanceOf(
            BackupError.Invalid::class.java,
            error(BackupCodec.encode(BackupFile(preferences = entries)))
        )
        val words = List(BackupCodec.MAX_WORDS + 1) { BackupWord("es", "w$it") }
        assertInstanceOf(
            BackupError.Invalid::class.java,
            error(BackupCodec.encode(BackupFile(userWords = words)))
        )
    }
}

class SettingsBackupTest {
    private val files = mutableListOf<File>()

    private fun temp(suffix: String): File = Files.createTempFile("backup", suffix).toFile().apply {
        delete()
        files += this
    }

    @AfterEach
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun CoroutineScope.store() = PreferenceDataStoreFactory.create(scope = this) {
        temp(".preferences_pb")
    }

    @Test
    fun `every preference type is exported and restored exactly`() = runTest {
        val source = store()
        source.edit {
            it[booleanPreferencesKey("b")] = true
            it[intPreferencesKey("i")] = 7
            it[longPreferencesKey("l")] = 1L shl 40
            it[floatPreferencesKey("f")] = 1.5f
            it[doublePreferencesKey("d")] = 2.25
            it[stringPreferencesKey("s")] = "text with \"quotes\" and ñ"
            it[stringSetPreferencesKey("set")] = setOf("x", "y")
        }
        val backup = SettingsBackup(
            source,
            UserWordsRepository(temp(".txt"))
        ).create("1.0.0", "now")
        val text = BackupCodec.encode(backup)
        val target = store()
        val summary = SettingsBackup(target, UserWordsRepository(temp(".txt")))
            .restore((BackupCodec.decode(text) as BackupLoadResult.Loaded).file)
        assertEquals(7, summary.preferences)
        assertEquals(
            source.data.first().asMap().mapKeys {
                it.key.name
            },
            target.data.first().asMap().mapKeys { it.key.name }
        )
    }

    @Test
    fun `restoring replaces what was there instead of mixing`() = runTest {
        val target = store()
        target.edit {
            it[intPreferencesKey("old_only")] = 1
            it[intPreferencesKey("shared")] = 1
        }
        val backup = BackupFile(
            preferences = listOf(BackupEntry("shared", "int", JsonPrimitive(2)))
        )
        SettingsBackup(target, UserWordsRepository(temp(".txt"))).restore(backup)
        val prefs = target.data.first()
        assertNull(prefs[intPreferencesKey("old_only")])
        assertEquals(2, prefs[intPreferencesKey("shared")])
    }

    @Test
    fun `custom styles and the active style come back with the settings`() = runTest {
        val source = store()
        val styles = DataStoreStyleRepository(source)
        val saved = styles.save(Style(id = "mine", name = "Mine"))
        styles.select(saved.id)
        val backup = SettingsBackup(
            source,
            UserWordsRepository(temp(".txt"))
        ).create("1.0.0", "now")
        val target = store()
        SettingsBackup(target, UserWordsRepository(temp(".txt"))).restore(backup)
        val restored = DataStoreStyleRepository(target)
        assertEquals("mine", restored.activeId.first())
        assertEquals(listOf("Mine"), restored.custom.first().map { it.name })
        assertTrue(Presets.isPreset(Presets.default.id))
    }

    @Test
    fun `words are added to the dictionary and the ones already there are kept`() = runTest {
        val words = UserWordsRepository(temp(".txt"))
        words.add("es", "hola")
        val backup = BackupFile(
            userWords = listOf(
                BackupWord("es", "hola"),
                BackupWord("en", "zork"),
                BackupWord("es", "   ")
            )
        )
        val seen = mutableListOf<UserWord>()
        val summary = SettingsBackup(store(), words).restore(backup) { seen += it }
        assertEquals(1, summary.wordsAdded)
        assertEquals(listOf(UserWord("en", "zork")), seen)
        assertEquals(
            setOf(UserWord("es", "hola"), UserWord("en", "zork")),
            words.words.value.toSet()
        )
    }
}
