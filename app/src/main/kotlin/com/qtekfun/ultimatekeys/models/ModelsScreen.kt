// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.models

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatekeys.Heading
import com.qtekfun.ultimatekeys.LabeledSwitch
import com.qtekfun.ultimatekeys.MinTouchTarget
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.voicemodels.DownloadFailure
import com.qtekfun.ultimatekeys.voicemodels.DownloadState
import com.qtekfun.ultimatekeys.voicemodels.ImportRejection
import com.qtekfun.ultimatekeys.voicemodels.ImportUiState
import com.qtekfun.ultimatekeys.voicemodels.ModelRow
import com.qtekfun.ultimatekeys.voicemodels.ModelsController
import com.qtekfun.ultimatekeys.voicemodels.ModelsUiState

@Suppress("LongParameterList")
@Composable
internal fun ModelsScreen(
    state: ModelsUiState,
    controller: ModelsController,
    onWifiOnly: (Boolean) -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text(stringResource(R.string.models_back)) }
        Heading(stringResource(R.string.models_title), MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(
                if (state.usesNetwork) R.string.models_intro_lite else R.string.models_intro_full
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onSettings, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text(stringResource(R.string.dictation_settings_open)) }
        if (state.usesNetwork) {
            LabeledSwitch(stringResource(R.string.model_wifi_only), state.wifiOnly, onWifiOnly)
            Text(
                stringResource(R.string.model_wifi_note),
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.rows.forEach { row -> ModelCard(row, state.wifiOnly, state.usesNetwork, controller) }
        OutlinedButton(onClick = onImport, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text(stringResource(R.string.model_action_import)) }
        ImportMessage(state.import, controller)
    }
}

@Composable
private fun ModelCard(
    row: ModelRow,
    wifiOnly: Boolean,
    usesNetwork: Boolean,
    controller: ModelsController
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${row.name} (${Formatter.formatShortFileSize(context, row.bytes)})",
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(noteOf(row)), style = MaterialTheme.typography.bodySmall)
            row.spec?.let {
                Text(
                    stringResource(R.string.model_license, it.license),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(stateText(row), style = MaterialTheme.typography.bodyMedium)
            (row.download as? DownloadState.Downloading)?.let {
                LinearProgressIndicator(progress = {
                    it.fraction
                }, modifier = Modifier.fillMaxWidth())
            }
            (row.download as? DownloadState.Failed)?.let {
                Text(
                    stringResource(failureText(it.reason)),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Actions(row, wifiOnly, usesNetwork, controller)
            }
        }
    }
}

@Composable
private fun Actions(
    row: ModelRow,
    wifiOnly: Boolean,
    usesNetwork: Boolean,
    controller: ModelsController
) {
    val busy = row.download is DownloadState.Queued || row.download is DownloadState.Downloading ||
        row.download is DownloadState.Verifying
    when {
        busy -> OutlinedButton(onClick = { controller.cancel(row.id) }, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            Text(stringResource(R.string.model_action_cancel))
        }

        row.installed -> {
            if (!row.active) {
                Button(
                    onClick = { controller.select(row.id) },
                    modifier = Modifier.heightIn(min = MinTouchTarget)
                ) { Text(stringResource(R.string.model_action_use)) }
            }
            OutlinedButton(onClick = { controller.delete(row.id) }, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                Text(stringResource(R.string.model_action_delete))
            }
        }

        row.canProvide -> Button(onClick = { controller.download(row.id, wifiOnly) }, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            val label = when {
                row.download is DownloadState.Failed -> R.string.model_action_retry
                usesNetwork -> R.string.model_action_download
                else -> R.string.model_action_restore
            }
            Text(stringResource(label))
        }
    }
}

@Composable
private fun stateText(row: ModelRow): String {
    val context = LocalContext.current
    return when (val download = row.download) {
        is DownloadState.Queued -> stringResource(R.string.model_state_queued_wifi)

        is DownloadState.Downloading -> stringResource(
            if (row.spec != null &&
                !row.installed
            ) {
                R.string.model_state_downloading
            } else {
                R.string.model_state_copying
            },
            Formatter.formatShortFileSize(context, download.bytes),
            Formatter.formatShortFileSize(context, download.total)
        )

        is DownloadState.Verifying -> stringResource(R.string.model_state_verifying)

        else -> stringResource(
            when {
                row.active -> R.string.model_state_active
                row.installed -> R.string.model_state_installed
                else -> R.string.model_state_available
            }
        )
    }
}

private fun noteOf(row: ModelRow): Int = when (row.id) {
    "base" -> R.string.model_note_base
    "small" -> R.string.model_note_small
    else -> R.string.model_note_imported
}

private fun failureText(reason: DownloadFailure): Int = when (reason) {
    DownloadFailure.NETWORK -> R.string.model_failure_network
    DownloadFailure.SERVER_BUSY -> R.string.model_failure_server_busy
    DownloadFailure.SERVER_REFUSED -> R.string.model_failure_refused
    DownloadFailure.CHECKSUM -> R.string.model_failure_checksum
    DownloadFailure.NO_SPACE -> R.string.model_failure_no_space
    DownloadFailure.STORAGE -> R.string.model_failure_storage
}

private fun rejectionText(reason: ImportRejection): Int = when (reason) {
    ImportRejection.NOT_GGML -> R.string.model_import_not_ggml
    ImportRejection.NOT_WHISPER -> R.string.model_import_not_whisper
    ImportRejection.TOO_SMALL -> R.string.model_import_too_small
    ImportRejection.TOO_LARGE -> R.string.model_import_too_large
}

@Composable
private fun ImportMessage(import: ImportUiState, controller: ModelsController) {
    val context = LocalContext.current
    when (import) {
        ImportUiState.Idle -> Unit

        ImportUiState.Working -> Text(stringResource(R.string.model_import_working))

        is ImportUiState.ConfirmUnknown -> AlertDialog(
            onDismissRequest = controller::dismissImport,
            title = { Text(stringResource(R.string.model_unknown_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.model_unknown_message,
                        Formatter.formatShortFileSize(context, import.bytes),
                        import.sha256.take(CHECKSUM_SHOWN) + "…"
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = controller::confirmImport, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                    Text(stringResource(R.string.model_unknown_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = controller::dismissImport, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                    Text(stringResource(R.string.model_action_cancel))
                }
            }
        )

        is ImportUiState.Imported -> Message(R.string.model_import_done, controller)

        is ImportUiState.Rejected -> Message(rejectionText(import.reason), controller)

        ImportUiState.ReadFailed -> Message(R.string.model_import_failed, controller)
    }
}

@Composable
private fun Message(text: Int, controller: ModelsController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(text), Modifier.weight(1f))
        TextButton(onClick = controller::dismissImport, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("OK") }
    }
}

private const val CHECKSUM_SHOWN = 16
