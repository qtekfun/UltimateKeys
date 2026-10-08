// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voicemodels

import com.qtekfun.ultimatekeys.voice.ModelSource
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * How a flavor gets a catalog model onto the device: the `lite` flavor downloads it, the `full`
 * flavor can restore its bundled one. Implementations live in the app's flavor source sets.
 */
interface ModelProvisioner {
    /** True when getting a model uses the network (the person is told, and may require Wi-Fi). */
    val usesNetwork: Boolean

    /** Progress of every model being fetched, by model id. */
    val states: Flow<Map<String, DownloadState>>

    fun canProvide(spec: ModelSpec): Boolean

    fun start(spec: ModelSpec, wifiOnly: Boolean)

    /** Stops the fetch and discards what was fetched so far. */
    fun cancel(spec: ModelSpec)
}

/** No model can be fetched (an app flavor with nothing bundled and no downloader). */
object NoProvisioner : ModelProvisioner {
    override val usesNetwork = false
    override val states: Flow<Map<String, DownloadState>> = MutableStateFlow(emptyMap())

    override fun canProvide(spec: ModelSpec) = false

    override fun start(spec: ModelSpec, wifiOnly: Boolean) = Unit

    override fun cancel(spec: ModelSpec) = Unit
}

/**
 * Copies a model that ships inside the APK into the model directory, once. A marker file records
 * that this was done, so a model the person deleted stays deleted (it can still be restored by hand).
 */
class BundledModelInstaller(
    private val store: ModelStore,
    private val open: (assetPath: String) -> InputStream
) {
    /** Installs [spec] from [assetPath] unless it is installed or was installed before. True when it copied. */
    fun installOnce(spec: ModelSpec, assetPath: String): Boolean {
        val marker = marker(spec)
        if (marker.isFile || store.isInstalled(spec)) return false
        install(spec, assetPath)
        return true
    }

    /** Installs [spec] from the bundle now, even if the person deleted it before. */
    fun install(spec: ModelSpec, assetPath: String, onProgress: (Long) -> Unit = {}) {
        store.install(spec, open(assetPath).buffered(), onProgress)
        marker(spec).writeText(spec.sha256)
    }

    // Next to the models, not in staging (which is cleaned).
    private fun marker(spec: ModelSpec): File = File(store.directory, ".bundled-${spec.id}")
}

/** The model to dictate with: the person's choice when installed, else the first installed one. */
class SelectedModelSource(private val store: ModelStore, private val selectedId: () -> String) :
    ModelSource {
    override fun currentModel(): File? {
        val models = store.installed()
        val wanted = selectedId()
        return (models.firstOrNull { it.id == wanted } ?: models.firstOrNull())?.file
    }
}

/** A tiny holder used by flavors to publish progress for [ModelProvisioner.states]. */
class ProgressBoard {
    private val flow = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = flow

    fun set(id: String, state: DownloadState) = flow.update { it + (id to state) }

    fun clear(id: String) = flow.update { it - id }
}
