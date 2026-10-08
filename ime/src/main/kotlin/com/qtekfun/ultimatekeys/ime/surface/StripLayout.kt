// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.ime.suggest.SuggestionState

/** What the suggestion bar shows: the words, the private-mode button and the dictation button. */
data class StripState(
    val words: List<String> = emptyList(),
    val isPrivate: Boolean = false,
    val showToggle: Boolean = false,
    val showMic: Boolean = false
)

/**
 * Splits the bar into the private-mode button on the left and the dictation button on the right
 * (each as wide as the bar is tall) and the suggestion slots between them. Shared by the renderer
 * and the touch handling so they always agree.
 */
class StripLayout(
    val width: Float,
    val height: Float,
    showToggle: Boolean,
    showMic: Boolean = false
) {
    val toggleWidth: Float = if (showToggle) height else 0f
    val micWidth: Float = if (showMic) height else 0f
    val cellWidth: Float = (width - toggleWidth - micWidth) / SuggestionState.SLOTS

    fun isToggle(x: Float): Boolean = toggleWidth > 0f && x < toggleWidth

    fun isMic(x: Float): Boolean = micWidth > 0f && x >= width - micWidth

    fun slotAt(x: Float): Int =
        ((x - toggleWidth) / cellWidth).toInt().coerceIn(0, SuggestionState.SLOTS - 1)
}
