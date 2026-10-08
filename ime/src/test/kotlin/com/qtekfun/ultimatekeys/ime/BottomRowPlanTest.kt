// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import com.qtekfun.ultimatekeys.layouts.BottomSlot
import com.qtekfun.ultimatekeys.style.BottomRowArrangement
import com.qtekfun.ultimatekeys.style.BottomRowStyle
import com.qtekfun.ultimatekeys.style.MicPlacement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BottomRowPlanTest {
    private val none = BottomRowFeatures()
    private val all = BottomRowFeatures(emoji = true, voice = true)

    private fun slots(arrangement: BottomRowArrangement, mic: MicPlacement, f: BottomRowFeatures) =
        BottomRowPlan.slots(BottomRowStyle(arrangement, mic), f)

    @Test
    fun `the default keeps the globe, comma and period`() {
        assertEquals(
            listOf(
                BottomSlot.SWITCH,
                BottomSlot.GLOBE,
                BottomSlot.COMMA,
                BottomSlot.SPACE,
                BottomSlot.PERIOD,
                BottomSlot.ENTER
            ),
            BottomRowPlan.slots(BottomRowStyle(), none)
        )
    }

    @Test
    fun `emoji arrangement falls back to the globe until the emoji panel exists`() {
        val a = BottomRowArrangement.SYMBOLS_EMOJI_SPACE_PERIOD_ENTER
        assertEquals(BottomSlot.GLOBE, slots(a, MicPlacement.BOTTOM_ROW, none)[1])
        assertEquals(BottomSlot.EMOJI, slots(a, MicPlacement.BOTTOM_ROW, all)[1])
    }

    @Test
    fun `mic sits in the bottom row only when voice exists and the style asks for it`() {
        val a = BottomRowArrangement.SYMBOLS_COMMA_SPACE_MIC_ENTER
        assertEquals(BottomSlot.PERIOD, slots(a, MicPlacement.BOTTOM_ROW, none)[3])
        assertEquals(BottomSlot.MIC, slots(a, MicPlacement.BOTTOM_ROW, all)[3])
        assertEquals(BottomSlot.PERIOD, slots(a, MicPlacement.SUGGESTION_BAR, all)[3])
    }

    @Test
    fun `the globe arrangement is just four keys`() {
        assertEquals(
            listOf(BottomSlot.SWITCH, BottomSlot.GLOBE, BottomSlot.SPACE, BottomSlot.ENTER),
            slots(BottomRowArrangement.SYMBOLS_GLOBE_SPACE_ENTER, MicPlacement.BOTTOM_ROW, all)
        )
    }
}
