// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatekeys.Heading
import com.qtekfun.ultimatekeys.LabeledSlider
import com.qtekfun.ultimatekeys.MinTouchTarget
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import kotlin.math.roundToInt

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
    onBack: () -> Unit
) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text(stringResource(R.string.models_back)) }
        Text(
            stringResource(R.string.dictation_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Heading(stringResource(R.string.dictation_language))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LANGUAGES.forEach { (code, label) ->
                FilterChip(
                    selected = settings.dictationLanguage == code,
                    onClick = { update { it.copy(dictationLanguage = code) } },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget)
                )
            }
        }
        Text(
            stringResource(R.string.dictation_language_note),
            style = MaterialTheme.typography.bodySmall
        )
        val ms = settings.dictationSilenceMs
        val seconds = "${ms / MS_PER_SECOND}.${ms % MS_PER_SECOND / TENTH_MS}"
        LabeledSlider(
            label = stringResource(R.string.dictation_silence_label),
            valueText = stringResource(R.string.dictation_silence_value, seconds),
            value = ms.toFloat(),
            range = KeyboardSettings.DICTATION_SILENCE_RANGE.first.toFloat()..
                KeyboardSettings.DICTATION_SILENCE_RANGE.last.toFloat(),
            onChange = { v ->
                val stepped = (v / SILENCE_STEP_MS).roundToInt() * SILENCE_STEP_MS
                update { it.copy(dictationSilenceMs = stepped) }
            }
        )
        Text(
            stringResource(R.string.dictation_silence_note),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private const val MS_PER_SECOND = 1000
private const val TENTH_MS = 100
