// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** Whether the keyboard is enabled in the system and whether it is the active one. */
data class ImeStatus(val enabled: Boolean, val selected: Boolean) {
    companion object {
        fun read(context: Context): ImeStatus {
            val imm = context.getSystemService(InputMethodManager::class.java)
            val enabled = imm.enabledInputMethodList.any { it.packageName == context.packageName }
            val current = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            return ImeStatus(enabled, current?.startsWith(context.packageName + "/") == true)
        }
    }
}
