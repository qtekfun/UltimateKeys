// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.models

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.core.settingsRepository
import com.qtekfun.ultimatekeys.voicemodels.ModelImporter
import com.qtekfun.ultimatekeys.voicemodels.ModelsController
import com.qtekfun.ultimatekeys.voicemodels.modelCatalog
import com.qtekfun.ultimatekeys.voicemodels.modelStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The dictation model manager and the dictation settings; the keyboard's "Choose a model" opens it. */
class ModelsActivity : ComponentActivity() {
    private val viewModel: ModelsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startOnSettings = intent.getBooleanExtra(EXTRA_SETTINGS, false)
        setContent {
            val scheme = if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(this)
            } else {
                dynamicLightColorScheme(this)
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) {
                    var onSettings by rememberSaveable { mutableStateOf(startOnSettings) }
                    val state by viewModel.controller.state.collectAsState()
                    val settings by viewModel.settings.collectAsState()
                    val picker = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        uri?.let {
                            viewModel.controller.import { contentResolver.openInputStream(it) }
                        }
                    }
                    if (onSettings) {
                        DictationSettingsScreen(
                            settings = settings,
                            update = viewModel::update,
                            onBack = { onSettings = false }
                        )
                    } else {
                        ModelsScreen(
                            state = state,
                            controller = viewModel.controller,
                            onWifiOnly = { v ->
                                viewModel.update { it.copy(modelDownloadWifiOnly = v) }
                            },
                            onImport = { picker.launch(arrayOf("*/*")) },
                            onSettings = { onSettings = true },
                            onBack = ::finish
                        )
                    }
                }
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
