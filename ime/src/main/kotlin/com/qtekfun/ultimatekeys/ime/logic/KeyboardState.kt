// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.logic

enum class ShiftState { OFF, ONCE, LOCKED }

enum class Page { LETTERS, SYMBOLS_1, SYMBOLS_2, NUMERIC, PHONE }

/** What the keyboard surface shows; the only state it observes from the logic. */
data class KeyboardState(
    val shift: ShiftState = ShiftState.OFF,
    val page: Page = Page.LETTERS,
    val enterKind: EnterKind = EnterKind.ENTER
)
