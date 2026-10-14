// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatekeys.AppVersion
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.core.BackupCodec
import com.qtekfun.ultimatekeys.core.BackupError
import com.qtekfun.ultimatekeys.core.BackupFile
import com.qtekfun.ultimatekeys.core.BackupLoadResult
import com.qtekfun.ultimatekeys.core.settingsBackup
import com.qtekfun.ultimatekeys.engine.SharedEngine
import com.qtekfun.ultimatekeys.ui.ScreenInsets
import com.qtekfun.ultimatekeys.ui.UkActionRow
import com.qtekfun.ultimatekeys.ui.section
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Export and restore of the app's data to a file the person chooses. */
@Composable
internal fun BackupScreen(onBack: () -> Unit, insets: ScreenInsets = ScreenInsets.current()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<BackupFile?>(null) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) scope.launch { export(context, uri) }
    }
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = readBackup(context, uri)) {
                    is BackupLoadResult.Loaded -> pending = result.file
                    is BackupLoadResult.Failed -> toast(context, errorText(context, result.error))
                }
            }
        }
    }

    val exportTitle = stringResource(R.string.backup_export)
    val restoreTitle = stringResource(R.string.backup_restore)
    val fileName = "ultimatekeys-${LocalDate.now()}.${BackupCodec.FILE_EXTENSION}"
    SubScreen(R.string.section_backup, onBack, insets) {
        section(footer = R.string.backup_note) {
            row { UkActionRow(exportTitle, onClick = { exporter.launch(fileName) }) }
            row { UkActionRow(restoreTitle, onClick = { importer.launch(arrayOf("*/*")) }) }
        }
    }

    pending?.let { file ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_confirm_body,
                        file.createdAt.take(DATE_CHARS).ifBlank { "?" },
                        file.appVersion.ifBlank { "?" },
                        file.preferences.size,
                        file.userWords.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        scope.launch { restore(context, file) }
                    }
                ) { Text(stringResource(R.string.backup_confirm_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(R.string.backup_confirm_cancel))
                }
            }
        )
    }
}

private const val DATE_CHARS = 10

private fun errorText(context: Context, error: BackupError): String = context.getString(
    when (error) {
        BackupError.TooLarge -> R.string.backup_error_too_large
        BackupError.NotJson, BackupError.NotABackup -> R.string.backup_error_not_backup
        is BackupError.NewerVersion -> R.string.backup_error_newer
        is BackupError.Invalid -> R.string.backup_error_invalid
    }
)

private fun toast(context: Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()

private suspend fun export(context: Context, uri: Uri) {
    val ok = withContext(Dispatchers.IO) {
        try {
            val file = context.settingsBackup()
                .create(AppVersion.read(context).name, Instant.now().toString())
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(BackupCodec.encode(file).toByteArray(Charsets.UTF_8))
            } != null
        } catch (_: IOException) {
            false
        }
    }
    toast(
        context,
        context.getString(if (ok) R.string.backup_exported else R.string.backup_error_write)
    )
}

private suspend fun readBackup(context: Context, uri: Uri): BackupLoadResult =
    withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use {
                it.readUpTo(BackupCodec.MAX_BYTES + 1)
            }
            if (bytes == null) {
                BackupLoadResult.Failed(BackupError.NotJson)
            } else {
                BackupCodec.decode(bytes.toString(Charsets.UTF_8))
            }
        } catch (_: IOException) {
            BackupLoadResult.Failed(BackupError.NotJson)
        }
    }

private suspend fun restore(context: Context, file: BackupFile) {
    val summary = withContext(Dispatchers.IO) {
        context.settingsBackup().restore(file) { word ->
            SharedEngine.instance.addToUserDictionary(
                word.word,
                Locale.forLanguageTag(word.language)
            )
        }
    }
    toast(
        context,
        context.getString(R.string.backup_restored, summary.preferences, summary.wordsAdded)
    )
}

/** Reads at most [limit] bytes, so a huge file cannot fill the memory. */
private fun java.io.InputStream.readUpTo(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

private const val BUFFER_SIZE = 8192
