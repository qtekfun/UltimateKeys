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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.FileProvider
import com.qtekfun.ultimatekeys.R
import com.qtekfun.ultimatekeys.core.styleRepository
import com.qtekfun.ultimatekeys.ime.surface.KeyboardPreview
import com.qtekfun.ultimatekeys.ime.surface.PreviewContent
import com.qtekfun.ultimatekeys.ime.surface.SurfaceStyle
import com.qtekfun.ultimatekeys.style.Presets
import com.qtekfun.ultimatekeys.style.Style
import com.qtekfun.ultimatekeys.style.StyleCodec
import com.qtekfun.ultimatekeys.style.StyleLoadError
import com.qtekfun.ultimatekeys.style.StyleLoadResult
import com.qtekfun.ultimatekeys.style.StyleRepository
import com.qtekfun.ultimatekeys.style.asNewCustom
import com.qtekfun.ultimatekeys.ui.UkBarButton
import com.qtekfun.ultimatekeys.ui.UkChevron
import com.qtekfun.ultimatekeys.ui.UkContentRow
import com.qtekfun.ultimatekeys.ui.UkGlyph
import com.qtekfun.ultimatekeys.ui.UkIcon
import com.qtekfun.ultimatekeys.ui.UkRadius
import com.qtekfun.ultimatekeys.ui.UkRow
import com.qtekfun.ultimatekeys.ui.UkScreen
import com.qtekfun.ultimatekeys.ui.UkSize
import com.qtekfun.ultimatekeys.ui.UkTheme
import com.qtekfun.ultimatekeys.ui.group
import com.qtekfun.ultimatekeys.ui.setUkContent
import java.io.File
import kotlinx.coroutines.launch

/** Lists the built-in and custom styles; picks, edits, duplicates, imports and exports them. */
class StylesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = styleRepository()
        setUkContent { StylesScreen(repository, onClose = ::finish) }
    }
}

@Composable
private fun StylesScreen(repository: StyleRepository, onClose: () -> Unit) {
    val context = LocalContext.current
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
            onBack = { editing = null }
        )
        return
    }

    val back = stringResource(R.string.nav_back)
    val builtInLabel = stringResource(R.string.style_builtin)
    UkScreen(
        title = stringResource(R.string.styles_title),
        onBack = onClose,
        backText = back,
        backDescription = back,
        actions = {
            UkBarButton(
                stringResource(R.string.style_import),
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                bold = false
            )
        }
    ) {
        (Presets.all + custom).forEach { style ->
            val builtIn = Presets.isPreset(style.id)
            group(key = style.id) {
                row { StylePreview(style, dark) }
                row {
                    StyleSelectRow(
                        style = style,
                        active = style.id == activeId,
                        builtInLabel = if (builtIn) builtInLabel else null,
                        onUse = { scope.launch { repository.select(style.id) } }
                    )
                }
                row {
                    StyleMenuRow(
                        style = style,
                        builtIn = builtIn,
                        actions = CardActions(
                            onEdit = { editing = style },
                            onDuplicate = {
                                editing = asNewCustom(style, custom, "${style.name} $copySuffix")
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
}

private class CardActions(
    val onEdit: () -> Unit,
    val onDuplicate: () -> Unit,
    val onExport: () -> Unit,
    val onShare: () -> Unit,
    val onDelete: () -> Unit
)

/** The keyboard drawn in the style, at a thumbnail size; the picture is described, not read key by key. */
@Composable
private fun StylePreview(style: Style, systemDark: Boolean) {
    val previewLabel = stringResource(R.string.style_preview, style.name)
    UkContentRow(Modifier.clearAndSetSemantics { contentDescription = previewLabel }) {
        Box(Modifier.clip(RoundedCornerShape(UkRadius.badge))) {
            KeyboardPreview(
                style = style,
                dark = SurfaceStyle.isDark(style, systemDark),
                content = PreviewContent(heightPercent = THUMB_HEIGHT_PERCENT)
            )
        }
    }
}

/** The style's name; tapping it makes it the one in use (a radio button of the whole list). */
@Composable
private fun StyleSelectRow(
    style: Style,
    active: Boolean,
    builtInLabel: String?,
    onUse: () -> Unit
) {
    val inUse = stringResource(R.string.style_active)
    val subtitle = listOfNotNull(builtInLabel, style.description.takeIf { it.isNotBlank() })
        .joinToString(" · ").ifBlank { null }
    UkRow(
        title = style.name,
        subtitle = subtitle,
        onClick = onUse,
        role = Role.RadioButton,
        state = if (active) inUse else null,
        trailing = {
            if (active) {
                UkIcon(UkGlyph.Check, UkTheme.colors.tint, size = UkSize.badgeGlyph)
                Text(inUse, style = UkTheme.typography.footnote, color = UkTheme.colors.tint)
            }
        }
    )
}

/** "Options": a menu of what can be done with the style; every item names the style for TalkBack. */
@Composable
private fun StyleMenuRow(style: Style, builtIn: Boolean, actions: CardActions) {
    var open by remember { mutableStateOf(false) }
    val items = buildList {
        if (!builtIn) add(MenuItem(R.string.style_edit, actions.onEdit))
        add(MenuItem(R.string.style_duplicate, actions.onDuplicate))
        add(MenuItem(R.string.style_export, actions.onExport))
        add(MenuItem(R.string.style_share, actions.onShare))
        if (!builtIn) add(MenuItem(R.string.style_delete, actions.onDelete))
    }
    Box {
        UkRow(
            title = stringResource(R.string.style_options),
            onClick = { open = true },
            role = Role.DropdownList,
            trailing = { UkChevron(pointingDown = true) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                val label = stringResource(item.label)
                val described = stringResource(R.string.a11y_action_on_style, label, style.name)
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        item.run()
                    },
                    modifier = Modifier.semantics { contentDescription = described }
                )
            }
        }
    }
}

private class MenuItem(val label: Int, val run: () -> Unit)

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
