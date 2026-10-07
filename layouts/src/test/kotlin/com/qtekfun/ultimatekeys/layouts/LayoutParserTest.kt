// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.layouts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayoutParserTest {
    @Test
    fun `parses plain strings, objects and action keys`() {
        val layout = LayoutParser.parse(
            """{"id":"t","locale":"en","rows":[["a",{"l":"b","o":"B","lp":["1","x"],"w":2},{"type":"delete","w":1.5}]]}"""
        )
        assertEquals("t", layout.id)
        assertEquals("en", layout.locale)
        val keys = layout.rows.single().keys
        assertEquals(CharKey("a"), keys[0])
        assertEquals(CharKey("b", "B", listOf("1", "x"), "1", 2f), keys[1])
        assertEquals(ActionKey(KeyAction.DELETE, width = 1.5f), keys[2])
    }

    @Test
    fun `rejects malformed layouts`() {
        listOf(
            "not json",
            """{"rows":[["a"]]}""",
            """{"id":"x"}""",
            """{"id":"x","rows":[]}""",
            """{"id":"x","rows":[[]]}""",
            """{"id":"x","rows":[[{"type":"nope"}]]}""",
            """{"id":"x","rows":[[{"w":1}]]}""",
            """{"id":"x","rows":[[{"l":"a","w":0}]]}""",
            """{"id":"x","rows":["a"]}"""
        ).forEach { json ->
            assertThrows(LayoutParseException::class.java, { LayoutParser.parse(json) }, json)
        }
    }

    @Test
    fun `every bundled layout parses and has an enter and delete or is a letter layout`() {
        listOf(
            "es_qwerty",
            "en_qwerty",
            "symbols_1",
            "symbols_2",
            "numeric",
            "phone"
        ).forEach { id ->
            val actions = LayoutRepository.load(id).rows.flatMap {
                it.keys
            }.filterIsInstance<ActionKey>().map { it.action }
            assertTrue(KeyAction.ENTER in actions, id)
            assertTrue(KeyAction.DELETE in actions, id)
        }
    }

    @Test
    fun `spanish layout has enye and accent alternatives`() {
        val keys = LayoutRepository.load("es_qwerty").rows.flatMap {
            it.keys
        }.filterIsInstance<CharKey>()
        assertTrue(keys.any { it.label == "ñ" })
        val a = keys.first { it.label == "a" }
        assertTrue("á" in a.alternatives && "à" in a.alternatives)
        assertEquals("1", keys.first { it.label == "q" }.hint)
    }

    @Test
    fun `english layout has no enye key but offers it on n`() {
        val keys = LayoutRepository.load("en_qwerty").rows.flatMap {
            it.keys
        }.filterIsInstance<CharKey>()
        assertTrue(keys.none { it.label == "ñ" })
        assertTrue("ñ" in keys.first { it.label == "n" }.alternatives)
    }

    @Test
    fun `number row variant adds a row and drops digit hints`() {
        val plain = LayoutRepository.pages("es_qwerty", numberRow = false).letters
        val withRow = LayoutRepository.pages("es_qwerty", numberRow = true).letters
        assertEquals(plain.rows.size + 1, withRow.rows.size)
        val q = withRow.rows[1].keys.first() as CharKey
        assertNull(q.hint)
        assertTrue("1" !in q.alternatives)
        assertEquals("1", (withRow.rows[0].keys.first() as CharKey).label)
    }

    @Test
    fun `unknown layout fails clearly`() {
        assertThrows(LayoutParseException::class.java) { LayoutRepository.load("missing") }
    }
}
