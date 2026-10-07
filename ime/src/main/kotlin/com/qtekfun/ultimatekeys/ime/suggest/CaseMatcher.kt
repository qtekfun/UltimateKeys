// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.suggest

/** Gives a suggestion the capitalization the user is typing with. */
object CaseMatcher {
    fun match(typed: String, suggestion: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } &&
            typed.any { it.isLetter() } ->
            suggestion.uppercase()

        typed.firstOrNull()?.isUpperCase() == true -> suggestion.replaceFirstChar { it.uppercase() }

        else -> suggestion
    }
}
