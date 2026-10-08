// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.suggest

import com.qtekfun.ultimatekeys.engine.SuggestionEngine
import java.util.Locale

/**
 * The single choke point that keeps private mode honest: while [isPrivate] says so, [learn] does
 * nothing, whoever calls it. Looking words up (suggestions, validity) is unaffected, and so are the
 * person's own explicit dictionary edits, which are not learning.
 */
class PrivacyGuardedEngine(
    private val delegate: SuggestionEngine,
    private val isPrivate: () -> Boolean
) : SuggestionEngine by delegate {
    override fun learn(word: String, context: List<String>, locale: Locale) {
        if (isPrivate()) return
        delegate.learn(word, context, locale)
    }
}
