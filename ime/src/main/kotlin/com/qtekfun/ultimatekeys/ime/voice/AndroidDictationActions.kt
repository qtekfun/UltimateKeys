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

    // The model manager lives in the app module (which owns the flavors), so it is started by name.
    override fun openModels() {
        val intent = Intent()
            .setClassName(context.packageName, MODELS_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private companion object {
        const val MODELS_ACTIVITY = "com.qtekfun.ultimatekeys.models.ModelsActivity"
    }
}
