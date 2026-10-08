// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.ime.suggest.SuggestionState

/** What the suggestion bar shows: the words, the private-mode button, the tools and the dictation button. */
data class StripState(
    val words: List<String> = emptyList(),
    val isPrivate: Boolean = false,
    val showToggle: Boolean = false,
    val showMic: Boolean = false,
    /** The clipboard and emoji buttons after the private-mode button. */
    val showTools: Boolean = false,
    /** The microphone in the bottom margin, when the style puts it there. */
    val marginMic: MarginMic? = null
)

/**
 * Splits the bar into the private-mode button on the left (as wide as the bar is tall), the
 * [extraTools] buttons after it (clipboard, emoji), the dictation button on the right and the
 * suggestion slots between them. Shared by the renderer and the touch handling so they always agree.
 */
class StripLayout(
    val width: Float,
    val height: Float,
    showToggle: Boolean,
    showMic: Boolean = false,
    val extraTools: Int = 0
) {
    val toggleWidth: Float = if (showToggle) height else 0f
    val micWidth: Float = if (showMic) height else 0f

    /** Everything left of the suggestion slots. */
    val toolsWidth: Float = toggleWidth + extraTools * height
    val cellWidth: Float = (width - toolsWidth - micWidth) / SuggestionState.SLOTS

    fun isToggle(x: Float): Boolean = toggleWidth > 0f && x < toggleWidth

    fun isMic(x: Float): Boolean = micWidth > 0f && x >= width - micWidth

    /** Which extra button [x] is on (0 is the first after the toggle), or -1 when it is none. */
    fun extraToolAt(x: Float): Int =
        if (x < toggleWidth || x >= toolsWidth) -1 else ((x - toggleWidth) / height).toInt()

    fun slotAt(x: Float): Int =
        ((x - toolsWidth) / cellWidth).toInt().coerceIn(0, SuggestionState.SLOTS - 1)
}
