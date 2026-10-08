// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.ime.logic.EnterKind
import com.qtekfun.ultimatekeys.ime.logic.KeyboardState

/** What a preview shows; fixed text so screenshots and the editor are stable. */
data class PreviewContent(
    val layoutId: String = "en_qwerty",
    val numberRow: Boolean = false,
    val suggestions: List<String> = listOf("keyboard", "keys", "key"),
    val state: KeyboardState = KeyboardState(enterKind = EnterKind.ENTER),
    val heightPercent: Int = 100,
    val private: Boolean = false,
    /** The language being typed: its case rules decide how capitals are drawn. */
    val locale: java.util.Locale = java.util.Locale.ROOT,
    val showToggle: Boolean = true,
    val features: com.qtekfun.ultimatekeys.ime.BottomRowFeatures =
        com.qtekfun.ultimatekeys.ime.BottomRowFeatures()
)
