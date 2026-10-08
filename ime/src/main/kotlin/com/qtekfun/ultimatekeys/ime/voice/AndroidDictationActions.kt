// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.voice

import android.content.Context
import android.content.Intent
import com.qtekfun.ultimatekeys.voice.MicrophonePermissionFlow

/** [DictationActions] on a real context: activities are started from the keyboard service. */
class AndroidDictationActions(private val context: Context) : DictationActions {
    override fun requestMicrophone() = MicrophonePermissionFlow.request(context)

    override fun openAppSettings() = MicrophonePermissionFlow.openAppSettings(context)

    // Until the model manager exists, the app's own screen is where models will be chosen.
    override fun openModels() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
    }
}
