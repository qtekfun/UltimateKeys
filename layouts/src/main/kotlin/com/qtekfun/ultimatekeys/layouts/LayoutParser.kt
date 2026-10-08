// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.layouts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LayoutParseException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/**
 * Parses the layout JSON format.
 *
 * A key is either a plain string (a character key) or an object with `l` (label), `o` (output),
 * `lp` (long-press alternatives), `w` (relative width) or `type` (an action key).
 */
object LayoutParser {
    private val actionTypes = mapOf(
        "shift" to KeyAction.SHIFT,
        "delete" to KeyAction.DELETE,
        "enter" to KeyAction.ENTER,
        "space" to KeyAction.SPACE,
        "globe" to KeyAction.GLOBE,
        "emoji" to KeyAction.EMOJI,
        "mic" to KeyAction.MIC,
        "switch_letters" to KeyAction.SWITCH_LETTERS,
        "switch_symbols" to KeyAction.SWITCH_SYMBOLS,
        "switch_symbols_2" to KeyAction.SWITCH_SYMBOLS_2
    )

    fun parse(json: String): KeyboardLayout {
        val root = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (e: IllegalArgumentException) {
            throw LayoutParseException("Invalid layout JSON: ${e.message}", e)
        }
        val id = root.string("id") ?: throw LayoutParseException("Layout without id")
        val rows = (
            root["rows"] as? JsonArray
                ?: throw LayoutParseException("Layout $id has no rows")
            )
            .map { parseRow(id, it) }
        if (rows.isEmpty() || rows.any { it.keys.isEmpty() }) {
            throw LayoutParseException("Layout $id has an empty row")
        }
        return KeyboardLayout(id = id, locale = root.string("locale"), rows = rows)
    }

    private fun parseRow(layoutId: String, element: JsonElement): KeyRow {
        val array =
            element as? JsonArray
                ?: throw LayoutParseException("Layout $layoutId: a row must be an array")
        return KeyRow(array.map { parseKey(layoutId, it) })
    }

    private fun parseKey(layoutId: String, element: JsonElement): LayoutKey {
        if (element is JsonPrimitive) {
            val text =
                element.contentOrNull ?: throw LayoutParseException("Layout $layoutId: null key")
            return CharKey(label = text)
        }
        val obj = element.jsonObject
        val width = obj["w"]?.jsonPrimitive?.floatOrNull ?: 1f
        if (width <= 0f) throw LayoutParseException("Layout $layoutId: key width must be positive")
        val type = obj.string("type")
        if (type != null) {
            val action =
                actionTypes[type]
                    ?: throw LayoutParseException("Layout $layoutId: unknown key type $type")
            return ActionKey(action, label = obj.string("label"), width = width)
        }
        val label =
            obj.string("l") ?: throw LayoutParseException("Layout $layoutId: key without label")
        val alternatives = obj["lp"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val hint = alternatives.firstOrNull()?.takeIf { it.length == 1 && it[0].isDigit() }
        return CharKey(
            label = label,
            output = obj.string("o") ?: label,
            alternatives = alternatives,
            hint = hint,
            width = width
        )
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
