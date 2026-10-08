// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voicemodels

import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line of the model manager. [spec] is null for a model the person imported. */
data class ModelRow(
    val id: String,
    val name: String,
    val spec: ModelSpec?,
    val bytes: Long,
    val installed: Boolean,
    val active: Boolean,
    val download: DownloadState,
    val canProvide: Boolean
)

/** Builds the manager's list; pure so it can be tested without a device. */
object ModelListing {
    fun rows(
        catalog: ModelCatalog,
        installed: List<InstalledModel>,
        downloads: Map<String, DownloadState>,
        selectedId: String,
        provisioner: ModelProvisioner
    ): List<ModelRow> {
        // Dictation uses the selected model when it is installed, otherwise the first installed one.
        val activeId = (
            installed.firstOrNull {
                it.id == selectedId
            } ?: installed.firstOrNull()
            )?.id
        val byId = installed.associateBy { it.id }
        val fromCatalog = catalog.models.map { spec ->
            val model = byId[spec.id]
            ModelRow(
                id = spec.id,
                name = spec.name,
                spec = spec,
                bytes = model?.bytes ?: spec.bytes,
                installed = model != null,
                active = model != null && spec.id == activeId,
                download = if (model !=
                    null
                ) {
                    DownloadState.Idle
                } else {
                    downloads[spec.id] ?: DownloadState.Idle
                },
                canProvide = model == null && provisioner.canProvide(spec)
            )
        }
        val others = installed.filter { it.spec == null }.map {
            ModelRow(
                it.id,
                it.file.name,
                null,
                it.bytes,
                true,
                it.id == activeId,
                DownloadState.Idle,
                false
            )
        }
        return fromCatalog + others
    }
}

sealed interface ImportUiState {
    data object Idle : ImportUiState

    data object Working : ImportUiState

    /** A Whisper-looking file that is not a known model: shown until the person confirms or cancels. */
    data class ConfirmUnknown(val sha256: String, val bytes: Long) : ImportUiState

    data class Imported(val modelId: String, val wasRecognized: Boolean) : ImportUiState

    data class Rejected(val reason: ImportRejection) : ImportUiState

    data object ReadFailed : ImportUiState
}

data class ModelsUiState(
    val rows: List<ModelRow> = emptyList(),
    val import: ImportUiState = ImportUiState.Idle,
    val wifiOnly: Boolean = true,
    val usesNetwork: Boolean = false
)

/**
 * The model manager's logic: the list, choosing, deleting, fetching through the flavor's
 * [ModelProvisioner] and importing files. The screen only draws [state] and calls these.
 */
@Suppress("LongParameterList")
class ModelsController(
    private val scope: CoroutineScope,
    private val catalog: ModelCatalog,
    private val store: ModelStore,
    private val importer: ModelImporter,
    private val provisioner: ModelProvisioner,
    selectedId: Flow<String>,
    wifiOnly: Flow<Boolean>,
    private val setSelected: suspend (String) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val refresh = MutableStateFlow(0)
    private val importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    private var pending: ImportInspection? = null

    val state: StateFlow<ModelsUiState> = combine(
        selectedId,
        wifiOnly,
        provisioner.states,
        refresh,
        importState
    ) { selected, wifi, downloads, _, import ->
        ModelsUiState(
            rows = ModelListing.rows(catalog, store.installed(), downloads, selected, provisioner),
            import = import,
            wifiOnly = wifi,
            usesNetwork = provisioner.usesNetwork
        )
    }.flowOn(
        io
    ).stateIn(scope, SharingStarted.Eagerly, ModelsUiState(usesNetwork = provisioner.usesNetwork))

    fun reload() = refresh.update { it + 1 }

    fun select(id: String) {
        scope.launch { setSelected(id) }
    }

    fun delete(id: String) {
        scope.launch(io) {
            store.installed().firstOrNull { it.id == id }?.let(store::delete)
            reload()
        }
    }

    fun download(id: String, wifiOnly: Boolean) {
        catalog.find(id)?.takeIf(provisioner::canProvide)?.let { provisioner.start(it, wifiOnly) }
    }

    fun cancel(id: String) {
        catalog.find(id)?.let(provisioner::cancel)
    }

    /** Looks at the picked file ([open] gives its bytes). Unknown files wait for [confirmImport]. */
    fun import(open: () -> InputStream?) {
        importState.value = ImportUiState.Working
        scope.launch(io) {
            importState.value = try {
                val input = open()
                if (input == null) {
                    ImportUiState.ReadFailed
                } else {
                    input.use { inspect(it) }
                }
            } catch (_: IOException) {
                ImportUiState.ReadFailed
            }
            reload()
        }
    }

    private fun inspect(input: InputStream): ImportUiState {
        pending?.let(importer::discard)
        pending = null
        return when (val result = importer.inspect(input)) {
            is ImportInspection.Rejected -> ImportUiState.Rejected(result.reason)

            is ImportInspection.Recognized -> {
                val model = importer.accept(result)
                ImportUiState.Imported(model?.id.orEmpty(), wasRecognized = true)
            }

            is ImportInspection.Unrecognized -> {
                pending = result
                ImportUiState.ConfirmUnknown(result.sha256, result.bytes)
            }
        }
    }

    /** The person confirmed the unknown file: install it. */
    fun confirmImport() {
        val inspection = pending ?: return
        pending = null
        scope.launch(io) {
            importState.value = try {
                val model = importer.accept(inspection)
                ImportUiState.Imported(model?.id.orEmpty(), wasRecognized = false)
            } catch (_: IOException) {
                ImportUiState.ReadFailed
            }
            reload()
        }
    }

    /** Drops the pending unknown file, or just closes a finished message. */
    fun dismissImport() {
        pending?.let(importer::discard)
        pending = null
        importState.value = ImportUiState.Idle
    }
}
