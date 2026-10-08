// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.models

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.settings.ValueFormat
import com.qtekfun.ultimatekeys.settings.choice
import com.qtekfun.ultimatekeys.settings.intSlider
import com.qtekfun.ultimatekeys.settings.toggle
import com.qtekfun.ultimatekeys.ui.ScreenInsets
import com.qtekfun.ultimatekeys.ui.UkNavRow
import com.qtekfun.ultimatekeys.ui.UkOption
import com.qtekfun.ultimatekeys.ui.UkScreen
import com.qtekfun.ultimatekeys.ui.group
import com.qtekfun.ultimatekeys.ui.section

private val LANGUAGES = listOf(
    "auto" to R.string.dictation_language_auto,
    "es" to R.string.dictation_language_es,
    "en" to R.string.dictation_language_en
)

private const val SILENCE_STEP_MS = 100

@Composable
internal fun DictationSettingsScreen(
    settings: KeyboardSettings,
    update: ((KeyboardSettings) -> KeyboardSettings) -> Unit,
    onModels: () -> Unit,
    onBack: () -> Unit,
    insets: ScreenInsets = ScreenInsets.current()
) {
    val languages = LANGUAGES.map { (code, label) -> UkOption(code, stringResource(label)) }
    UkScreen(
        title = stringResource(R.string.dictation_title),
        insets = insets,
        onBack = onBack,
        backText = stringResource(R.string.nav_back),
        backDescription = stringResource(R.string.nav_back)
    ) {
        group {
            toggle(R.string.setting_dictation_enabled, settings.dictationEnabled) { v ->
                update { it.copy(dictationEnabled = v) }
            }
        }
        section(footer = R.string.dictation_language_note) {
            choice(R.string.dictation_language, languages, settings.dictationLanguage) { v ->
                update { it.copy(dictationLanguage = v) }
            }
        }
        section(footer = R.string.dictation_silence_note) {
            intSlider(
                R.string.dictation_silence_label,
                settings.dictationSilenceMs,
                KeyboardSettings.DICTATION_SILENCE_RANGE,
                { ValueFormat.seconds(it) },
                step = SILENCE_STEP_MS
            ) { v -> update { it.copy(dictationSilenceMs = v) } }
        }
        group {
            row { UkNavRow(stringResource(R.string.models_open), onClick = onModels) }
        }
    }
}
