// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.core.KeyboardSettings

/** Where the dictation button sits in the margin under the keys. */
enum class MicSide {
    LEFT,
    CENTER,
    RIGHT;

    companion object {
        /** The side for a stored setting value; anything unknown means the left. */
        fun of(setting: String): MicSide = when (setting) {
            KeyboardSettings.MIC_CENTER -> CENTER
            KeyboardSettings.MIC_RIGHT -> RIGHT
            else -> LEFT
        }
    }
}
