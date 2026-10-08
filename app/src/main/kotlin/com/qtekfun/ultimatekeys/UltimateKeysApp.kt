// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import android.app.Application
import android.util.Log
import com.qtekfun.ultimatekeys.models.installBundledModels
import com.qtekfun.ultimatekeys.voicemodels.modelStore
import java.io.IOException

/** Prepares the dictation models in the background when the process starts (keyboard or settings). */
class UltimateKeysApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread({ prepareModels() }, "uk-models").start()
    }

    private fun prepareModels() {
        try {
            modelStore().cleanStaging()
            installBundledModels(this)
        } catch (e: IOException) {
            // Dictation reports "no model" and the manager offers to restore it; typing is unaffected.
            Log.w("UltimateKeys", "Could not prepare the dictation models", e)
        }
    }
}
