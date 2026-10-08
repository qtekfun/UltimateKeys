// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.suggest

import java.util.Locale

/** Gives a suggestion the capitalization the user is typing with, using the case rules of [locale] (Turkish i). */
object CaseMatcher {
    fun match(typed: String, suggestion: String, locale: Locale = Locale.ROOT): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } &&
            typed.any { it.isLetter() } ->
            suggestion.uppercase(locale)

        typed.firstOrNull()?.isUpperCase() == true -> suggestion.replaceFirstChar {
            it.uppercase(locale)
        }

        else -> suggestion
    }
}
