// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.layouts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class BottomRowTest {
    private val letters = LayoutRepository.load("es_qwerty")
    private val symbols = LayoutRepository.load("symbols_1")

    private fun bottom(layout: KeyboardLayout) = layout.rows.last().keys

    private fun describe(keys: List<LayoutKey>) = keys.map {
        when (it) {
            is CharKey -> it.label
            is ActionKey -> it.action.name
        }
    }

    @Test
    fun `rebuilds the bottom row from the slots in order`() {
        val slots = listOf(BottomSlot.SWITCH, BottomSlot.GLOBE, BottomSlot.SPACE, BottomSlot.ENTER)
        val result = BottomRow.apply(letters, slots)
        assertEquals(
            listOf("SWITCH_SYMBOLS", "GLOBE", "SPACE", "ENTER"),
            describe(bottom(result))
        )
        assertEquals(letters.rows.size, result.rows.size)
        assertEquals(letters.rows.dropLast(1), result.rows.dropLast(1))
    }

    @Test
    fun `the space bar takes the width the other keys leave`() {
        val old = bottom(letters).sumOf { it.width.toDouble() }
        val result = BottomRow.apply(
            letters,
            listOf(BottomSlot.SWITCH, BottomSlot.GLOBE, BottomSlot.SPACE, BottomSlot.ENTER)
        )
        assertEquals(old, bottom(result).sumOf { it.width.toDouble() }, 0.001)
    }

    @Test
    fun `the switch key matches the page`() {
        val slots = listOf(BottomSlot.SWITCH, BottomSlot.SPACE, BottomSlot.ENTER)
        assertEquals("SWITCH_LETTERS", describe(bottom(BottomRow.apply(symbols, slots))).first())
    }

    @Test
    fun `comma and period keep their long press alternatives`() {
        val result = BottomRow.apply(
            letters,
            listOf(
                BottomSlot.SWITCH,
                BottomSlot.COMMA,
                BottomSlot.SPACE,
                BottomSlot.PERIOD,
                BottomSlot.ENTER
            )
        )
        val period = bottom(result)[3] as CharKey
        val original = bottom(letters).filterIsInstance<CharKey>().first { it.label == "." }
        assertEquals(original, period)
    }

    @Test
    fun `emoji and mic slots become action keys`() {
        val result = BottomRow.apply(
            letters,
            listOf(
                BottomSlot.SWITCH,
                BottomSlot.EMOJI,
                BottomSlot.SPACE,
                BottomSlot.MIC,
                BottomSlot.ENTER
            )
        )
        assertEquals(
            listOf("SWITCH_SYMBOLS", "EMOJI", "SPACE", "MIC", "ENTER"),
            describe(bottom(result))
        )
    }

    @Test
    fun `layouts without a space bar are left alone`() {
        val bare = KeyboardLayout("bare", null, listOf(KeyRow(listOf(CharKey("1"), CharKey("2")))))
        assertSame(bare, BottomRow.apply(bare, listOf(BottomSlot.SPACE)))
    }
}
