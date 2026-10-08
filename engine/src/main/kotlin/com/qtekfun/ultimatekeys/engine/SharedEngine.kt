// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

/** The one engine of this process: the keyboard service and the settings screens use the same. */
object SharedEngine {
    val instance = SwappableSuggestionEngine()
}
