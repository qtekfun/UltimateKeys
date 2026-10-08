// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.layouts

/** A place in the bottom row, left to right. */
enum class BottomSlot { SWITCH, GLOBE, COMMA, PERIOD, SPACE, EMOJI, MIC, ENTER }

/** Rearranges the bottom row of a layout (the row with the space bar) into the given slots. */
object BottomRow {
    private const val EDGE_WIDTH = 1.4f
    private const val SIDE_WIDTH = 1f
    private const val MIN_SPACE_WIDTH = 2f

    /** Returns [layout] with its bottom row rebuilt from [slots]; layouts without a space bar are unchanged. */
    fun apply(layout: KeyboardLayout, slots: List<BottomSlot>): KeyboardLayout {
        val index = layout.rows.indexOfLast { row ->
            row.keys.any { it is ActionKey && it.action == KeyAction.SPACE }
        }
        if (index < 0) return layout
        val old = layout.rows[index]
        val totalWidth = old.keys.sumOf { it.width.toDouble() }.toFloat()
        val fixed = slots.filter {
            it != BottomSlot.SPACE
        }.sumOf { widthOf(it).toDouble() }.toFloat()
        val spaceWidth = maxOf(totalWidth - fixed, MIN_SPACE_WIDTH)
        val keys = slots.mapNotNull { slot -> keyFor(slot, old, spaceWidth) }
        val rows = layout.rows.toMutableList().also { it[index] = KeyRow(keys) }
        return layout.copy(rows = rows)
    }

    private fun widthOf(slot: BottomSlot) = when (slot) {
        BottomSlot.SWITCH, BottomSlot.ENTER -> EDGE_WIDTH
        else -> SIDE_WIDTH
    }

    private fun keyFor(slot: BottomSlot, old: KeyRow, spaceWidth: Float): LayoutKey? = when (slot) {
        BottomSlot.SWITCH -> old.keys.firstOrNull { it is ActionKey && it.action in SWITCHES }
        BottomSlot.ENTER -> old.keys.firstOrNull { it is ActionKey && it.action == KeyAction.ENTER }
        BottomSlot.SPACE -> ActionKey(KeyAction.SPACE, width = spaceWidth)
        BottomSlot.GLOBE -> ActionKey(KeyAction.GLOBE, width = SIDE_WIDTH)
        BottomSlot.EMOJI -> ActionKey(KeyAction.EMOJI, width = SIDE_WIDTH)
        BottomSlot.MIC -> ActionKey(KeyAction.MIC, width = SIDE_WIDTH)
        BottomSlot.COMMA -> charKey(old, ",")
        BottomSlot.PERIOD -> charKey(old, ".")
    }

    /** The row's own key for [label] (it may carry long-press alternatives), or a plain one. */
    private fun charKey(old: KeyRow, label: String): LayoutKey =
        old.keys.firstOrNull { it is CharKey && it.label == label } ?: CharKey(label)

    private val SWITCHES = setOf(
        KeyAction.SWITCH_LETTERS,
        KeyAction.SWITCH_SYMBOLS,
        KeyAction.SWITCH_SYMBOLS_2
    )
}
