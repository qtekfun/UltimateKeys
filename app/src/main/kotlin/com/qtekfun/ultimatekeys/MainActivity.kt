// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.core.SettingsRepository
import com.qtekfun.ultimatekeys.core.settingsRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = settingsRepository()
        setContent {
            val scheme = if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(this)
            } else {
                dynamicLightColorScheme(this)
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { HomeScreen(repository) }
            }
        }
    }
}

@Composable
private fun HomeScreen(repository: SettingsRepository) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(ImeStatus.read(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = ImeStatus.read(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val settings by repository.settings.collectAsState(initial = KeyboardSettings())
    val scope = rememberCoroutineScope()
    fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        scope.launch { repository.update(transform) }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        SetupSteps(status)
        var sample by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = sample,
            onValueChange = { sample = it },
            label = { Text(stringResource(R.string.try_it_here)) },
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            context.startActivity(
                Intent(context, com.qtekfun.ultimatekeys.styles.StylesActivity::class.java)
            )
        }) {
            Text(stringResource(R.string.styles_open))
        }
        Button(onClick = {
            context.startActivity(Intent(context, UserDictionaryActivity::class.java))
        }) {
            Text(stringResource(R.string.user_dictionary_open))
        }
        SettingsSection(settings, ::update)
    }
}

@Composable
private fun SetupSteps(status: ImeStatus) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
            enabled = !status.enabled
        ) {
            Text(
                stringResource(if (status.enabled) R.string.step_enabled else R.string.step_enable)
            )
        }
        Button(
            onClick = {
                context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            },
            enabled = status.enabled && !status.selected
        ) {
            Text(
                stringResource(
                    if (status.selected) R.string.step_selected else R.string.step_select
                )
            )
        }
    }
}

@Composable
private fun SettingsSection(
    settings: KeyboardSettings,
    update: ((KeyboardSettings) -> KeyboardSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)
        SliderSetting(
            R.string.setting_height,
            settings.heightPercent,
            KeyboardSettings.HEIGHT_RANGE
        ) { v -> update { it.copy(heightPercent = v) } }
        SliderSetting(
            R.string.setting_bottom_margin,
            settings.bottomMarginDp,
            KeyboardSettings.BOTTOM_MARGIN_RANGE
        ) { v -> update { it.copy(bottomMarginDp = v) } }
        SliderSetting(
            R.string.setting_long_press,
            settings.longPressDelayMs,
            KeyboardSettings.LONG_PRESS_RANGE
        ) { v -> update { it.copy(longPressDelayMs = v) } }
        SliderSetting(
            R.string.setting_haptics,
            settings.hapticIntensity,
            KeyboardSettings.PERCENT_RANGE
        ) { v -> update { it.copy(hapticIntensity = v) } }
        SliderSetting(
            R.string.setting_sound,
            settings.soundVolume,
            KeyboardSettings.PERCENT_RANGE
        ) { v -> update { it.copy(soundVolume = v) } }
        SwitchSetting(R.string.setting_suggestions, settings.showSuggestions) { v ->
            update { it.copy(showSuggestions = v) }
        }
        SwitchSetting(R.string.setting_autocorrect, settings.autoCorrect) { v ->
            update { it.copy(autoCorrect = v) }
        }
        SwitchSetting(R.string.setting_number_row, settings.numberRow) { v ->
            update { it.copy(numberRow = v) }
        }
        SwitchSetting(R.string.setting_auto_capitalize, settings.autoCapitalize) { v ->
            update { it.copy(autoCapitalize = v) }
        }
        SwitchSetting(R.string.setting_double_space, settings.doubleSpacePeriod) { v ->
            update { it.copy(doubleSpacePeriod = v) }
        }
        SwitchSetting(
            R.string.setting_private_ends_on_close,
            settings.privateModeEndsOnClose
        ) { v ->
            update { it.copy(privateModeEndsOnClose = v) }
        }
        SwitchSetting(R.string.setting_smart_punctuation, settings.smartPunctuation) { v ->
            update { it.copy(smartPunctuation = v) }
        }
    }
}

@Composable
private fun SliderSetting(label: Int, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Text("${stringResource(label)}: $value")
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwitchSetting(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
