// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A backup of everything the person chose: every preference the app stores (settings, sizes per
 * screen, the active style and the custom styles) and the words added to the user dictionary.
 * Learned history, clipboard history, speech models and dictionaries are not included: the first two
 * are private and tied to one engine version, the others can be fetched again.
 */
@Serializable
data class BackupFile(
    val format: String = BackupCodec.FORMAT,
    val schemaVersion: Int = BackupCodec.CURRENT_VERSION,
    val appVersion: String = "",
    /** When it was made, as an ISO-8601 instant. Informational. */
    val createdAt: String = "",
    val preferences: List<BackupEntry> = emptyList(),
    val userWords: List<BackupWord> = emptyList()
)

/** One stored preference: its name, its type (see [BackupCodec.TYPES]) and its value. */
@Serializable
data class BackupEntry(val name: String, val type: String, val value: JsonElement)

@Serializable
data class BackupWord(val language: String, val word: String)

/** Why a backup file could not be read. */
sealed interface BackupError {
    data object TooLarge : BackupError

    data object NotJson : BackupError

    /** Valid JSON, but not one of our backups. */
    data object NotABackup : BackupError

    /** Written by a newer version of the app. */
    data class NewerVersion(val version: Int) : BackupError

    data class Invalid(val detail: String) : BackupError
}

sealed interface BackupLoadResult {
    data class Loaded(val file: BackupFile) : BackupLoadResult

    data class Failed(val error: BackupError) : BackupLoadResult
}

/** What a restore did, for the message shown afterwards. */
data class RestoreSummary(val preferences: Int, val wordsAdded: Int)

/**
 * Reads and writes backup files (JSON). Reading is strict about shape and limits, because the file
 * comes from outside: unknown names are kept only if they look like preference names, values must
 * match their declared type, and nothing larger than [MAX_BYTES] is accepted.
 */
object BackupCodec {
    const val FORMAT = "ultimatekeys-backup"
    const val CURRENT_VERSION = 1
    const val FILE_EXTENSION = "ukbackup"
    const val MAX_BYTES = 4 * 1024 * 1024

    const val MAX_ENTRIES = 500
    const val MAX_WORDS = 50_000
    private const val MAX_NAME = 64
    private const val MAX_TEXT = 3 * 1024 * 1024
    private const val MAX_SET = 1_000
    private const val MAX_DETAIL = 200
    internal val NAME = Regex("[a-z0-9_]{1,$MAX_NAME}")

    val TYPES = listOf("boolean", "int", "long", "float", "double", "string", "string_set")

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupLoadResult {
        if (text.length > MAX_BYTES) return BackupLoadResult.Failed(BackupError.TooLarge)
        val file = parse(text) ?: return BackupLoadResult.Failed(failureOf(text))
        val problem = problemWith(file)
        return if (problem == null) {
            BackupLoadResult.Loaded(file)
        } else {
            BackupLoadResult.Failed(problem)
        }
    }

    private fun parse(text: String): BackupFile? = try {
        json.decodeFromString(BackupFile.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun failureOf(text: String): BackupError {
        val isJson = try {
            Json.parseToJsonElement(text)
            true
        } catch (_: SerializationException) {
            false
        }
        return if (isJson) BackupError.NotABackup else BackupError.NotJson
    }

    /** What is wrong with [file], or null when it is fine to restore. */
    private fun problemWith(file: BackupFile): BackupError? = when {
        file.format != FORMAT -> BackupError.NotABackup
        file.schemaVersion > CURRENT_VERSION -> BackupError.NewerVersion(file.schemaVersion)
        file.preferences.size > MAX_ENTRIES -> BackupError.Invalid("too many settings")
        file.userWords.size > MAX_WORDS -> BackupError.Invalid("too many words")
        else -> entryProblem(file.preferences)
    }

    private fun entryProblem(entries: List<BackupEntry>): BackupError? {
        val seen = HashSet<String>()
        return entries.firstNotNullOfOrNull { entry ->
            when {
                !NAME.matches(entry.name) -> BackupError.Invalid("bad setting name")

                !seen.add(
                    entry.name
                ) -> BackupError.Invalid("repeated setting ${entry.name}".take(MAX_DETAIL))

                !isValue(
                    entry
                ) -> BackupError.Invalid("bad value for ${entry.name}".take(MAX_DETAIL))

                else -> null
            }
        }
    }

    private fun isValue(entry: BackupEntry): Boolean {
        val value = entry.value
        val primitive = value as? JsonPrimitive
        return when (entry.type) {
            "boolean" -> primitive?.booleanOrNull != null

            "int" -> primitive?.intOrNull != null

            "long" -> primitive?.longOrNull != null

            "float", "double" -> primitive?.doubleOrNull != null

            "string" ->
                primitive != null && primitive.isString &&
                    primitive.content.length <= MAX_TEXT

            "string_set" -> value is JsonArray && value.size <= MAX_SET && value.all {
                val item = it as? JsonPrimitive
                item != null && item.isString && item.content.length <= MAX_TEXT
            }

            else -> false
        }
    }
}

/** Turns the stored preferences into backup entries and back. */
internal object BackupPreferences {
    /** The entries of [preferences], in a stable order. */
    fun entriesOf(preferences: Preferences): List<BackupEntry> =
        preferences.asMap().mapNotNull { (key, value) -> entryOf(key.name, value) }
            .sortedBy { it.name }

    private fun entryOf(name: String, value: Any): BackupEntry? = when (value) {
        is Boolean -> BackupEntry(name, "boolean", JsonPrimitive(value))

        is Int -> BackupEntry(name, "int", JsonPrimitive(value))

        is Long -> BackupEntry(name, "long", JsonPrimitive(value))

        is Float -> BackupEntry(name, "float", JsonPrimitive(value))

        is Double -> BackupEntry(name, "double", JsonPrimitive(value))

        is String -> BackupEntry(name, "string", JsonPrimitive(value))

        is Set<*> -> BackupEntry(
            name,
            "string_set",
            JsonArray(value.filterIsInstance<String>().sorted().map(::JsonPrimitive))
        )

        else -> null
    }.takeIf { BackupCodec.NAME.matches(name) }

    /** Writes [entries] into [preferences] (already validated by [decode]). */
    fun apply(entries: List<BackupEntry>, preferences: MutablePreferences) {
        entries.forEach { put(preferences, it) }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun put(p: MutablePreferences, entry: BackupEntry) {
        val name = entry.name
        val value = entry.value as? JsonPrimitive
        when (entry.type) {
            "boolean" -> value?.booleanOrNull?.let { p[booleanPreferencesKey(name)] = it }

            "int" -> value?.intOrNull?.let { p[intPreferencesKey(name)] = it }

            "long" -> value?.longOrNull?.let { p[longPreferencesKey(name)] = it }

            "float" -> value?.doubleOrNull?.let { p[floatPreferencesKey(name)] = it.toFloat() }

            "double" -> value?.doubleOrNull?.let { p[doublePreferencesKey(name)] = it }

            "string" -> value?.let { p[stringPreferencesKey(name)] = it.content }

            "string_set" -> (entry.value as? JsonArray)?.let { array ->
                p[stringSetPreferencesKey(name)] =
                    array.mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
            }
        }
    }
}

/**
 * Creates and restores backups of the app's data. [words] is the user dictionary file repository;
 * the preferences are the DataStore all the settings and styles live in.
 */
class SettingsBackup(
    private val store: DataStore<Preferences>,
    private val words: UserWordsRepository
) {
    /** Reads the current data into a [BackupFile]. */
    suspend fun create(appVersion: String, createdAt: String): BackupFile = BackupFile(
        appVersion = appVersion,
        createdAt = createdAt,
        preferences = BackupPreferences.entriesOf(store.data.first()),
        userWords = words.words.value.map { BackupWord(it.language, it.word) }
    )

    /**
     * Replaces every stored preference with the backup's in one edit, so the app never sees a half
     * restored state, and adds the backup's words to the user dictionary (words already there are kept).
     * [onWordAdded] is called for each word that was new, so the engine can learn it.
     */
    suspend fun restore(file: BackupFile, onWordAdded: (UserWord) -> Unit = {}): RestoreSummary {
        store.edit { prefs ->
            prefs.clear()
            BackupPreferences.apply(file.preferences, prefs)
        }
        var added = 0
        for (entry in file.userWords) {
            if (words.add(entry.language, entry.word)) {
                added++
                onWordAdded(UserWord(entry.language.trim().lowercase(), entry.word.trim()))
            }
        }
        return RestoreSummary(file.preferences.size, added)
    }
}
