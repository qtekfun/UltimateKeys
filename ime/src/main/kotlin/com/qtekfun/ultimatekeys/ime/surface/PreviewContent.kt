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
    val heightPercent: Int = 100
)
