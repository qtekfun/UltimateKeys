// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The microphone permission needs an activity to ask from, and a keyboard has none. This is the
 * bridge: [request] shows a transparent activity that asks, and [results] tells the keyboard what
 * the person answered. The keyboard and the activity live in one process, so a plain flow works.
 */
object MicrophonePermissionFlow {
    private val mutableResults = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emits true when the permission was granted and false when it was refused. */
    val results: SharedFlow<Boolean> = mutableResults.asSharedFlow()

    /** Asks for the permission through a transparent activity. */
    fun request(context: Context) {
        context.startActivity(
            Intent(context, RecordAudioPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Opens the app's settings page, for when the system will not ask again. */
    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    internal fun deliver(granted: Boolean) {
        mutableResults.tryEmit(granted)
    }
}

/** Transparent activity that asks for RECORD_AUDIO and finishes at once. It shows no UI of its own. */
class RecordAudioPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // The system re-delivers the pending answer.
        if (checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            MicrophonePermissionFlow.deliver(true)
            finish()
        } else {
            requestPermissions(arrayOf(PERMISSION), REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        MicrophonePermissionFlow.deliver(granted)
        finish()
    }

    private companion object {
        const val PERMISSION = android.Manifest.permission.RECORD_AUDIO
        const val REQUEST_CODE = 1
    }
}
