// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

/** Removes what the model writes for sounds that are not speech and tidies whitespace. */
object TranscriptCleaner {
    // [BLANK_AUDIO], [ Silence ], [MUSIC]; *clears throat*; and notes for music.
    private val annotations = Regex("""\[[^\]]*]|\*[^*]*\*|[♪♫]+""")
    private val wholeParentheses = Regex("""^\(.*\)$""", RegexOption.DOT_MATCHES_ALL)
    private val whitespace = Regex("""\s+""")

    /** The spoken text, or an empty string when the model only heard noise. */
    fun clean(raw: String): String {
        val withoutAnnotations = annotations.replace(raw, " ")
        val collapsed = whitespace.replace(withoutAnnotations, " ").trim()
        // "(music)" or "(sighs)" as the whole output is an annotation too.
        return if (wholeParentheses.matches(collapsed)) "" else collapsed
    }
}
