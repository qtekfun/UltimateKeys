// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.ime.GLOBE_MENU_KEYBOARDS
import com.qtekfun.ultimatekeys.ime.GLOBE_MENU_SETTINGS
import com.qtekfun.ultimatekeys.ime.KeyboardController

/** Carries out what an accessibility node stands for, with the same calls the touch path makes. */
object AccessibilityTargets {
    fun activate(controller: KeyboardController, target: A11yTarget) {
        when (target) {
            is A11yTarget.Type -> {
                controller.keyDown(null)
                controller.onText(target.text)
            }

            is A11yTarget.Action -> {
                controller.keyDown(target.action)
                controller.onAction(target.action)
            }

            A11yTarget.KeyboardPicker -> controller.onGlobeMenu(GLOBE_MENU_KEYBOARDS)

            A11yTarget.OpenSettings -> controller.onGlobeMenu(GLOBE_MENU_SETTINGS)

            A11yTarget.TogglePrivate -> controller.togglePrivate()

            A11yTarget.OpenClipboard -> controller.openClipboard()

            is A11yTarget.Suggestion -> controller.onSuggestionTapped(target.slot)
        }
    }
}
