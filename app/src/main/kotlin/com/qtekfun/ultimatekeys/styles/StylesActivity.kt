// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.styles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.qtekfun.ultimatekeys.Heading
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.core.styleRepository
import com.qtekfun.ultimatekeys.ime.surface.KeyboardPreview
import com.qtekfun.ultimatekeys.ime.surface.PreviewContent
import com.qtekfun.ultimatekeys.style.Presets
import com.qtekfun.ultimatekeys.style.Style
import com.qtekfun.ultimatekeys.style.StyleCodec
import com.qtekfun.ultimatekeys.style.StyleLoadError
import com.qtekfun.ultimatekeys.style.StyleLoadResult
import com.qtekfun.ultimatekeys.style.StyleRepository
import com.qtekfun.ultimatekeys.style.asNewCustom
import java.io.File
import kotlinx.coroutines.launch

/** Lists the built-in and custom styles; picks, edits, duplicates, imports and exports them. */
class StylesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = styleRepository()
        setContent {
            val scheme = if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(this)
            } else {
                dynamicLightColorScheme(this)
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { StylesScreen(repository, onClose = ::finish) }
            }
        }
    }
}

@Composable
private fun StylesScreen(repository: StyleRepository, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val activeId by repository.activeId.collectAsState(initial = Presets.default.id)
    val custom by repository.custom.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Style?>(null) }
    var pendingExport by remember { mutableStateOf<Style?>(null) }
    val dark = isSystemInDarkTheme()
    val copySuffix = stringResource(R.string.style_copy_suffix)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val style = pendingExport
        if (uri != null && style != null) writeStyle(context, uri, style)
        pendingExport = null
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            when (val result = readStyle(context, uri)) {
                is StyleLoadResult.Loaded -> scope.launch {
                    repository.save(asNewCustom(result.style, custom))
                    toast(context, R.string.style_import_done)
                }

                is StyleLoadResult.Failed -> toast(
                    context,
                    if (result.error is StyleLoadError.NewerSchema) {
                        R.string.style_import_newer
                    } else {
                        R.string.style_import_failed
                    }
                )
            }
        }
    }

    val current = editing
    BackHandler(enabled = current != null) { editing = null }
    if (current != null) {
        StyleEditor(
            initial = current,
            onSave = { edited ->
                scope.launch {
                    val saved = repository.save(edited)
                    repository.select(saved.id)
                    editing = null
                }
            },
            onBack = { editing = null },
            modifier = Modifier.statusBarsPadding()
        )
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.style_back)) }
            Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Text(stringResource(R.string.style_import))
            }
        }
        Heading(
            stringResource(R.string.styles_title),
            MaterialTheme.typography.headlineSmall,
            Modifier.padding(vertical = 12.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Presets.all + custom, key = { it.id }) { style ->
                StyleCard(
                    style = style,
                    builtIn = Presets.isPreset(style.id),
                    active = style.id == activeId,
                    dark = dark,
                    actions = CardActions(
                        onUse = { scope.launch { repository.select(style.id) } },
                        onEdit = { editing = style },
                        onDuplicate = {
                            val copyName = "${style.name} $copySuffix"
                            editing = asNewCustom(style, custom, copyName)
                        },
                        onExport = {
                            pendingExport = style
                            exportLauncher.launch("${style.id}.${StyleCodec.FILE_EXTENSION}")
                        },
                        onShare = { shareStyle(context, style) },
                        onDelete = { scope.launch { repository.delete(style.id) } }
                    )
                )
            }
        }
    }
}

private class CardActions(
    val onUse: () -> Unit,
    val onEdit: () -> Unit,
    val onDuplicate: () -> Unit,
    val onExport: () -> Unit,
    val onShare: () -> Unit,
    val onDelete: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleCard(
    style: Style,
    builtIn: Boolean,
    active: Boolean,
    dark: Boolean,
    actions: CardActions
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val status = when {
                active -> stringResource(R.string.style_active)
                builtIn -> stringResource(R.string.style_builtin)
                else -> ""
            }
            Text(
                if (status.isEmpty()) style.name else "${style.name}  $status",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            if (style.description.isNotBlank()) Text(style.description)
            val previewLabel = stringResource(R.string.style_preview, style.name)
            Box(Modifier.clearAndSetSemantics { contentDescription = previewLabel }) {
                KeyboardPreview(
                    style = style,
                    dark = com.qtekfun.ultimatekeys.ime.surface.SurfaceStyle.isDark(style, dark),
                    content = PreviewContent(heightPercent = THUMB_HEIGHT_PERCENT)
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = actions.onUse,
                    enabled = !active,
                    modifier = Modifier.described(actionLabel(R.string.style_select, style.name))
                ) {
                    Text(stringResource(R.string.style_select))
                }
                if (!builtIn) {
                    OutlinedButton(
                        onClick = actions.onEdit,
                        modifier = Modifier.described(actionLabel(R.string.style_edit, style.name))
                    ) {
                        Text(stringResource(R.string.style_edit))
                    }
                }
                OutlinedButton(
                    onClick = actions.onDuplicate,
                    modifier = Modifier.described(actionLabel(R.string.style_duplicate, style.name))
                ) {
                    Text(stringResource(R.string.style_duplicate))
                }
                OutlinedButton(
                    onClick = actions.onExport,
                    modifier = Modifier.described(actionLabel(R.string.style_export, style.name))
                ) {
                    Text(stringResource(R.string.style_export))
                }
                OutlinedButton(
                    onClick = actions.onShare,
                    modifier = Modifier.described(actionLabel(R.string.style_share, style.name))
                ) {
                    Text(stringResource(R.string.style_share))
                }
                if (!builtIn) {
                    OutlinedButton(
                        onClick = actions.onDelete,
                        modifier = Modifier.described(
                            actionLabel(R.string.style_delete, style.name)
                        )
                    ) {
                        Text(stringResource(R.string.style_delete))
                    }
                }
            }
        }
    }
}

/** What a button says to a screen reader: its action and the style it acts on ("Edit, Classic"). */
@Composable
private fun actionLabel(action: Int, styleName: String): String =
    stringResource(R.string.a11y_action_on_style, stringResource(action), styleName)

private fun Modifier.described(text: String): Modifier = semantics { contentDescription = text }

/** Reads at most [limit] bytes, so a huge file cannot fill the memory. */
private fun java.io.InputStream.readUpTo(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

private fun toast(context: Context, message: Int) =
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

private fun readStyle(context: Context, uri: Uri): StyleLoadResult = try {
    val bytes = context.contentResolver.openInputStream(uri)?.use {
        it.readUpTo(StyleCodec.MAX_BYTES + 1)
    }
    if (bytes == null) {
        StyleLoadResult.Failed(StyleLoadError.NotJson)
    } else {
        StyleCodec.decode(bytes.toString(Charsets.UTF_8))
    }
} catch (_: java.io.IOException) {
    StyleLoadResult.Failed(StyleLoadError.NotJson)
}

private fun writeStyle(context: Context, uri: Uri, style: Style) {
    try {
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(StyleCodec.encode(style).toByteArray(Charsets.UTF_8))
        }
    } catch (_: java.io.IOException) {
        toast(context, R.string.style_import_failed)
    }
}

private fun shareStyle(context: Context, style: Style) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "${style.id}.${StyleCodec.FILE_EXTENSION}")
    file.writeText(StyleCodec.encode(style))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, style.name))
}

private const val THUMB_HEIGHT_PERCENT = 55
private const val BUFFER_SIZE = 8192
