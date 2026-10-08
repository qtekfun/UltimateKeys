// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.models

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.core.settingsRepository
import com.qtekfun.ultimatekeys.settings.rememberRouteStack
import com.qtekfun.ultimatekeys.ui.setUkContent
import com.qtekfun.ultimatekeys.voicemodels.ModelImporter
import com.qtekfun.ultimatekeys.voicemodels.ModelsController
import com.qtekfun.ultimatekeys.voicemodels.modelCatalog
import com.qtekfun.ultimatekeys.voicemodels.modelStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The screens of [ModelsActivity]: the dictation settings and, below them, the model manager. */
enum class ModelsRoute { Dictation, Models }

/** The dictation model manager and the dictation settings; the keyboard's "Choose a model" opens it. */
class ModelsActivity : ComponentActivity() {
    private val viewModel: ModelsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = if (intent.getBooleanExtra(EXTRA_SETTINGS, false)) {
            ModelsRoute.Dictation
        } else {
            ModelsRoute.Models
        }
        setUkContent {
            var stack by rememberRouteStack(start, ModelsRoute.entries)
            BackHandler(enabled = stack.canGoBack) { stack = stack.back() }
            val back = { if (stack.canGoBack) stack = stack.back() else finish() }
            val state by viewModel.controller.state.collectAsState()
            val settings by viewModel.settings.collectAsState()
            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                uri?.let { viewModel.controller.import { contentResolver.openInputStream(it) } }
            }
            when (stack.current) {
                ModelsRoute.Dictation -> DictationSettingsScreen(
                    settings = settings,
                    update = viewModel::update,
                    onModels = { stack = stack.open(ModelsRoute.Models) },
                    onBack = back
                )

                ModelsRoute.Models -> ModelsScreen(
                    state = state,
                    controller = viewModel.controller,
                    onWifiOnly = { v -> viewModel.update { it.copy(modelDownloadWifiOnly = v) } },
                    onImport = { picker.launch(arrayOf("*/*")) },
                    onSettings = { stack = stack.open(ModelsRoute.Dictation) },
                    onBack = back
                )
            }
        }
    }

    companion object {
        const val EXTRA_SETTINGS = "settings"
    }
}

/** Holds the model manager's logic across rotations. */
class ModelsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.settingsRepository()

    val settings = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        KeyboardSettings()
    )

    val controller = ModelsController(
        scope = viewModelScope,
        catalog = application.modelCatalog(),
        store = application.modelStore(),
        importer = ModelImporter(application.modelStore(), application.modelCatalog()),
        provisioner = createProvisioner(application),
        selectedId = repository.settings.map { it.dictationModelId }.distinctUntilChanged(),
        wifiOnly = repository.settings.map { it.modelDownloadWifiOnly }.distinctUntilChanged(),
        setSelected = { id -> repository.update { it.copy(dictationModelId = id) } }
    )

    fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
