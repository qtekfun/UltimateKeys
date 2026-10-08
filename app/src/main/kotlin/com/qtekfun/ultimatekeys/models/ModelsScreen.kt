// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.models

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.settings.toggle
import com.qtekfun.ultimatekeys.ui.ScreenInsets
import com.qtekfun.ultimatekeys.ui.UkActionRow
import com.qtekfun.ultimatekeys.ui.UkContentRow
import com.qtekfun.ultimatekeys.ui.UkGroupScope
import com.qtekfun.ultimatekeys.ui.UkNavRow
import com.qtekfun.ultimatekeys.ui.UkRow
import com.qtekfun.ultimatekeys.ui.UkScreen
import com.qtekfun.ultimatekeys.ui.UkSize
import com.qtekfun.ultimatekeys.ui.UkTheme
import com.qtekfun.ultimatekeys.ui.group
import com.qtekfun.ultimatekeys.ui.section
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
    onBack: () -> Unit,
    insets: ScreenInsets = ScreenInsets.current()
) {
    val introText =
        if (state.usesNetwork) R.string.models_intro_lite else R.string.models_intro_full
    UkScreen(
        title = stringResource(R.string.models_title),
        insets = insets,
        onBack = onBack,
        backText = stringResource(R.string.nav_back),
        backDescription = stringResource(R.string.nav_back)
    ) {
        section(footer = introText) {
            row {
                UkNavRow(stringResource(R.string.dictation_settings_open), onClick = onSettings)
            }
        }
        if (state.usesNetwork) {
            section(footer = R.string.model_wifi_note) {
                toggle(R.string.model_wifi_only, state.wifiOnly, onWifiOnly)
            }
        }
        state.rows.forEach { row ->
            group(key = "model-${row.id}") {
                modelRows(row, state.wifiOnly, state.usesNetwork, controller)
            }
        }
        group {
            row { UkActionRow(stringResource(R.string.model_action_import), onClick = onImport) }
            if (state.import != ImportUiState.Idle) row { ImportMessage(state.import, controller) }
        }
    }
}

/** A model as the rows of one group: what it is and how it stands, then what can be done to it. */
private fun UkGroupScope.modelRows(
    row: ModelRow,
    wifiOnly: Boolean,
    usesNetwork: Boolean,
    controller: ModelsController
) {
    row { ModelInfo(row) }
    val busy = row.download is DownloadState.Queued || row.download is DownloadState.Downloading ||
        row.download is DownloadState.Verifying
    when {
        busy -> row {
            UkActionRow(stringResource(R.string.model_action_cancel), onClick = {
                controller.cancel(row.id)
            })
        }

        row.installed -> {
            if (!row.active) {
                row {
                    UkActionRow(stringResource(R.string.model_action_use), onClick = {
                        controller.select(row.id)
                    })
                }
            }
            row {
                UkActionRow(
                    stringResource(R.string.model_action_delete),
                    onClick = { controller.delete(row.id) },
                    destructive = true
                )
            }
        }

        row.canProvide -> row {
            val label = when {
                row.download is DownloadState.Failed -> R.string.model_action_retry
                usesNetwork -> R.string.model_action_download
                else -> R.string.model_action_restore
            }
            UkActionRow(stringResource(label), onClick = { controller.download(row.id, wifiOnly) })
        }
    }
}

@Composable
private fun ModelInfo(row: ModelRow) {
    val context = LocalContext.current
    val colors = UkTheme.colors
    val type = UkTheme.typography
    UkContentRow {
        Text(
            "${row.name} (${Formatter.formatShortFileSize(context, row.bytes)})",
            style = type.body,
            color = colors.label
        )
        Text(stringResource(noteOf(row)), style = type.footnote, color = colors.secondaryLabel)
        row.spec?.let {
            Text(
                stringResource(R.string.model_license, it.license),
                style = type.footnote,
                color = colors.secondaryLabel
            )
        }
        Text(stateText(row), style = type.body, color = colors.tint)
        (row.download as? DownloadState.Downloading)?.let {
            LinearProgressIndicator(progress = { it.fraction }, modifier = Modifier.fillMaxWidth())
        }
        (row.download as? DownloadState.Failed)?.let {
            Text(
                stringResource(failureText(it.reason)),
                style = type.footnote,
                color = colors.destructive
            )
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

        ImportUiState.Working -> UkContentRow {
            Text(
                stringResource(R.string.model_import_working),
                style = UkTheme.typography.body,
                color = UkTheme.colors.label
            )
        }

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
                TextButton(
                    onClick = controller::confirmImport,
                    modifier = Modifier.heightIn(min = UkSize.minTouch)
                ) {
                    Text(stringResource(R.string.model_unknown_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = controller::dismissImport,
                    modifier = Modifier.heightIn(min = UkSize.minTouch)
                ) {
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
    UkRow(
        title = stringResource(text),
        onClick = controller::dismissImport,
        trailing = {
            Text(
                stringResource(R.string.model_import_dismiss),
                style = UkTheme.typography.body,
                color = UkTheme.colors.tint
            )
        }
    )
}

private const val CHECKSUM_SHOWN = 16
