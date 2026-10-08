// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

/** Which pair of colours is too close together. */
enum class ContrastPair {
    LABEL_ON_LETTER_KEY,
    LABEL_ON_FUNCTION_KEY,
    LABEL_ON_ACTION_KEY,
    SUGGESTION_TEXT,
    PANEL_TEXT
}

data class ContrastWarning(val dark: Boolean, val pair: ContrastPair, val ratio: Double)

/** WCAG AA check of the colour pairs people have to read (SPEC section 6). Warns, never blocks. */
object ContrastCheck {
    fun warnings(style: Style): List<ContrastWarning> =
        check(style.light, dark = false) + check(style.dark, dark = true)

    private fun check(p: Palette, dark: Boolean): List<ContrastWarning> = listOf(
        Triple(ContrastPair.LABEL_ON_LETTER_KEY, p.labelText, p.keyLetter),
        Triple(ContrastPair.LABEL_ON_FUNCTION_KEY, p.labelText, p.keyFunction),
        Triple(ContrastPair.LABEL_ON_ACTION_KEY, p.actionLabelText, p.keyAction),
        Triple(ContrastPair.SUGGESTION_TEXT, p.barText, p.barBackground),
        Triple(ContrastPair.PANEL_TEXT, p.panelText, p.panelSurface)
    ).mapNotNull { (pair, text, background) ->
        val ratio = contrastRatio(text, background)
        if (meetsAa(text, background)) null else ContrastWarning(dark, pair, ratio)
    }
}
