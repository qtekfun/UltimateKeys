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
        // The period gives way to the microphone when the style puts it in the row.
        val end = if (micInRow) BottomSlot.MIC else BottomSlot.PERIOD
        val emoji = if (features.emoji) BottomSlot.EMOJI else BottomSlot.GLOBE
        val sw = BottomSlot.SWITCH
        val enter = BottomSlot.ENTER
        val space = BottomSlot.SPACE
        val globe = BottomSlot.GLOBE
        val comma = BottomSlot.COMMA
        return when (style.arrangement) {
            // Two keys on each side of the space bar, so it sits in the middle under the thumbs.
            BottomRowArrangement.SYMBOLS_GLOBE_COMMA_SPACE_PERIOD_EMOJI_ENTER ->
                listOfNotNull(
                    sw,
                    globe,
                    comma,
                    space,
                    end,
                    BottomSlot.EMOJI.takeIf {
                        features.emoji
                    },
                    enter
                )

            BottomRowArrangement.SYMBOLS_GLOBE_COMMA_EMOJI_SPACE_PERIOD_ENTER ->
                listOfNotNull(
                    sw,
                    globe,
                    comma,
                    BottomSlot.EMOJI.takeIf {
                        features.emoji
                    },
                    space,
                    end,
                    enter
                )

            BottomRowArrangement.SYMBOLS_GLOBE_COMMA_SPACE_PERIOD_ENTER ->
                listOf(sw, globe, comma, space, end, enter)

            BottomRowArrangement.SYMBOLS_EMOJI_SPACE_PERIOD_ENTER ->
                listOf(sw, emoji, space, BottomSlot.PERIOD, enter)

            BottomRowArrangement.SYMBOLS_COMMA_SPACE_MIC_ENTER ->
                listOf(sw, comma, space, end, enter)

            BottomRowArrangement.SYMBOLS_GLOBE_SPACE_ENTER ->
                listOf(sw, globe, space, enter)
        }
    }
}
