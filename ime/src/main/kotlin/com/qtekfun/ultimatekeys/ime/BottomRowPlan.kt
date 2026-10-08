// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import com.qtekfun.ultimatekeys.layouts.BottomSlot
import com.qtekfun.ultimatekeys.style.BottomRowArrangement
import com.qtekfun.ultimatekeys.style.BottomRowStyle
import com.qtekfun.ultimatekeys.style.MicPlacement

/** Which optional keys exist yet. A key whose feature is missing is replaced by a useful one. */
data class BottomRowFeatures(val emoji: Boolean = false, val voice: Boolean = false)

/** Turns the style's bottom-row choice into the keys of the row. */
object BottomRowPlan {
    fun slots(style: BottomRowStyle, features: BottomRowFeatures): List<BottomSlot> {
        val micInRow = features.voice && style.micPlacement == MicPlacement.BOTTOM_ROW
        return when (style.arrangement) {
            BottomRowArrangement.SYMBOLS_GLOBE_COMMA_SPACE_PERIOD_ENTER -> listOf(
                BottomSlot.SWITCH,
                BottomSlot.GLOBE,
                BottomSlot.COMMA,
                BottomSlot.SPACE,
                if (micInRow) BottomSlot.MIC else BottomSlot.PERIOD,
                BottomSlot.ENTER
            )

            BottomRowArrangement.SYMBOLS_EMOJI_SPACE_PERIOD_ENTER -> listOf(
                BottomSlot.SWITCH,
                if (features.emoji) BottomSlot.EMOJI else BottomSlot.GLOBE,
                BottomSlot.SPACE,
                BottomSlot.PERIOD,
                BottomSlot.ENTER
            )

            BottomRowArrangement.SYMBOLS_COMMA_SPACE_MIC_ENTER -> listOf(
                BottomSlot.SWITCH,
                BottomSlot.COMMA,
                BottomSlot.SPACE,
                if (micInRow) BottomSlot.MIC else BottomSlot.PERIOD,
                BottomSlot.ENTER
            )

            BottomRowArrangement.SYMBOLS_GLOBE_SPACE_ENTER -> listOf(
                BottomSlot.SWITCH,
                BottomSlot.GLOBE,
                BottomSlot.SPACE,
                BottomSlot.ENTER
            )
        }
    }
}
