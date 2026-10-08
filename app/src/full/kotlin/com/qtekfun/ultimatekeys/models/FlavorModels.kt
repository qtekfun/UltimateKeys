// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.models

import android.content.Context
import com.qtekfun.ultimatekeys.voicemodels.BundledModelInstaller
import com.qtekfun.ultimatekeys.voicemodels.DownloadFailure
import com.qtekfun.ultimatekeys.voicemodels.DownloadState
import com.qtekfun.ultimatekeys.voicemodels.ModelProvisioner
import com.qtekfun.ultimatekeys.voicemodels.ModelSpec
import com.qtekfun.ultimatekeys.voicemodels.ProgressBoard
import com.qtekfun.ultimatekeys.voicemodels.modelCatalog
import com.qtekfun.ultimatekeys.voicemodels.modelStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/*
 * The `full` flavor: the `base` model ships inside the APK (see `fetchBundledModel` in
 * app/build.gradle.kts) and this flavor has no network code and no INTERNET permission.
 */

private const val BUNDLED_MODEL_ID = "base"

private fun assetPath(spec: ModelSpec) = "models/${spec.id}.ggml"

private fun installer(context: Context) =
    BundledModelInstaller(context.modelStore()) { context.assets.open(it) }

/** First run: copies the bundled model into `filesDir/models` (checksum verified). */
fun installBundledModels(context: Context) {
    val spec = context.modelCatalog().find(BUNDLED_MODEL_ID) ?: return
    installer(context).installOnce(spec, assetPath(spec))
}

fun createProvisioner(context: Context): ModelProvisioner =
    BundledProvisioner(context.applicationContext)

/** Restores the bundled model after the person deleted it. Nothing else can be fetched. */
private class BundledProvisioner(private val context: Context) : ModelProvisioner {
    private val board = ProgressBoard()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override val usesNetwork = false
    override val states: Flow<Map<String, DownloadState>> = board.states

    override fun canProvide(spec: ModelSpec) = spec.id == BUNDLED_MODEL_ID

    override fun start(spec: ModelSpec, wifiOnly: Boolean) {
        if (!canProvide(spec)) return
        board.set(spec.id, DownloadState.Downloading(0, spec.bytes))
        job = scope.launch {
            try {
                installer(context).install(spec, assetPath(spec)) {
                    board.set(spec.id, DownloadState.Downloading(it, spec.bytes))
                }
                board.clear(spec.id)
            } catch (_: IOException) {
                board.set(spec.id, DownloadState.Failed(DownloadFailure.STORAGE))
            }
        }
    }

    override fun cancel(spec: ModelSpec) {
        job?.cancel()
        board.clear(spec.id)
    }
}
