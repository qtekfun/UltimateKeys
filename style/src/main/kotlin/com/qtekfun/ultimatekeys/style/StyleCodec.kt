// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Why a style file could not be loaded. */
sealed interface StyleLoadError {
    data object TooLarge : StyleLoadError

    data object NotJson : StyleLoadError

    /** The file was written by a newer version of the app. */
    data class NewerSchema(val version: Int) : StyleLoadError

    data class Invalid(val detail: String) : StyleLoadError
}

sealed interface StyleLoadResult {
    data class Loaded(val style: Style) : StyleLoadResult

    data class Failed(val error: StyleLoadError) : StyleLoadResult
}

/**
 * Reads and writes `.ukstyle` files (JSON). Reading is forgiving about unknown keys, migrates old
 * schema versions and clamps every value; it refuses oversized files and files from the future.
 */
object StyleCodec {
    const val MAX_BYTES = 256 * 1024
    const val FILE_EXTENSION = "ukstyle"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** One step that upgrades a style object from schema version `from` to `from + 1`. */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = mapOf(1 to ::addGestureTrail)

    fun encode(style: Style): String = json.encodeToString(Style.serializer(), style.sanitized())

    /** Writes a list of styles as one JSON array (used for persistence, not for sharing). */
    fun encodeAll(styles: List<Style>): String =
        json.encodeToString(ListSerializer(Style.serializer()), styles.map { it.sanitized() })

    /** Reads a list written by [encodeAll]; entries that fail to load are dropped. */
    fun decodeAll(text: String): List<Style> {
        val array = try {
            json.parseToJsonElement(text) as? JsonArray
        } catch (_: SerializationException) {
            null
        } ?: return emptyList()
        return array.mapNotNull { (decode(it.toString()) as? StyleLoadResult.Loaded)?.style }
    }

    fun decode(text: String): StyleLoadResult {
        if (text.length > MAX_BYTES) return StyleLoadResult.Failed(StyleLoadError.TooLarge)
        return try {
            val root = json.parseToJsonElement(text) as? JsonObject
                ?: return StyleLoadResult.Failed(StyleLoadError.NotJson)
            val version = root["schemaVersion"]?.jsonPrimitive?.int ?: 1
            if (version > Style.CURRENT_SCHEMA_VERSION) {
                return StyleLoadResult.Failed(StyleLoadError.NewerSchema(version))
            }
            val migrated = migrate(root, version)
            val style = json.decodeFromJsonElement(Style.serializer(), migrated)
            StyleLoadResult.Loaded(style.sanitized())
        } catch (e: SerializationException) {
            failure(e)
        } catch (e: IllegalArgumentException) {
            failure(e)
        }
    }

    /**
     * Version 1 to 2: palettes gained the gesture trail colour (the key action colour is a fit for any
     * look) and the private mode tint, which version 1 files written before it existed lack.
     */
    private fun addGestureTrail(root: JsonObject): JsonObject {
        val updated = root.toMutableMap()
        for ((variant, fallbackTint) in listOf(
            "light" to Palette.DefaultLight,
            "dark" to Palette.DefaultDark
        )) {
            val palette = root[variant] as? JsonObject ?: continue
            val extra = palette.toMutableMap()
            if ("gestureTrail" !in extra) palette["keyAction"]?.let { extra["gestureTrail"] = it }
            if ("privateTint" !in extra) {
                extra["privateTint"] =
                    json.encodeToJsonElement(ArgbColor.serializer(), fallbackTint.privateTint)
            }
            updated[variant] = JsonObject(extra)
        }
        return JsonObject(updated)
    }

    private fun failure(e: Exception): StyleLoadResult =
        if (e.message?.contains("Unexpected JSON token") == true ||
            e.message?.contains("Expected start of the object") == true
        ) {
            StyleLoadResult.Failed(StyleLoadError.NotJson)
        } else {
            StyleLoadResult.Failed(StyleLoadError.Invalid(e.message.orEmpty().take(MAX_DETAIL)))
        }

    private fun migrate(root: JsonObject, from: Int): JsonObject {
        var current = root
        for (version in from until Style.CURRENT_SCHEMA_VERSION) {
            current = migrations.getValue(version)(current)
        }
        val stamped = current.toMutableMap()
        stamped["schemaVersion"] = JsonPrimitive(Style.CURRENT_SCHEMA_VERSION)
        return JsonObject(stamped)
    }

    private const val MAX_DETAIL = 200
}
