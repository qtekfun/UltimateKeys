// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("MagicNumber", "LongParameterList", "TooManyFunctions", "MatchingDeclarationName")

package com.qtekfun.ultimatekeys.ime.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qtekfun.ultimatekeys.clipboard.ClipItem
import com.qtekfun.ultimatekeys.ime.R

/** Everything the clipboard panel shows. */
internal data class ClipboardUi(
    val items: List<ClipItem>,
    /** Private mode is on: new copies are not saved. */
    val isPrivate: Boolean,
    /** The history is switched off in the settings. */
    val historyOff: Boolean
)

internal class ClipboardActions(
    val onPaste: (ClipItem) -> Unit,
    val onTogglePin: (ClipItem) -> Unit,
    val onDelete: (ClipItem) -> Unit,
    val onClearAll: () -> Unit,
    val onKeys: () -> Unit,
    val onSwitch: (PanelKind) -> Unit,
    val onDeleteText: () -> Unit
)

@Composable
internal fun ClipboardPanel(
    theme: PanelTheme,
    ui: ClipboardUi,
    actions: ClipboardActions,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(theme.background)) {
        Row(
            Modifier.fillMaxWidth().height(theme.barDp.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelText(
                stringResource(R.string.panel_clipboard),
                theme,
                Modifier.weight(1f),
                size = 16.sp
            )
            if (ui.items.any { !it.pinned }) {
                PanelButton(
                    theme,
                    stringResource(R.string.panel_clear_all),
                    actions.onClearAll,
                    Modifier.height(theme.barDp.dp - 12.dp),
                    selected = true
                ) {
                    PanelText(
                        stringResource(R.string.panel_clear_all),
                        theme,
                        Modifier.padding(horizontal = 12.dp),
                        size = 13.sp
                    )
                }
            }
        }
        if (ui.isPrivate) {
            Notice(theme, stringResource(R.string.panel_clipboard_private), tinted = true)
        } else if (ui.historyOff) {
            Notice(theme, stringResource(R.string.panel_clipboard_off), tinted = false)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (ui.items.isEmpty()) {
                Message(theme, stringResource(R.string.panel_clipboard_empty))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(ui.items, key = { it.id }) { ClipRow(theme, it, actions) }
                }
            }
        }
        PanelBottomBar(
            theme = theme,
            current = PanelKind.CLIPBOARD,
            tone = null,
            onKeys = actions.onKeys,
            onSwitch = actions.onSwitch,
            onDelete = actions.onDeleteText
        )
    }
}

@Composable
private fun Notice(theme: PanelTheme, text: String, tinted: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(theme.shape())
            .background(
                if (tinted) theme.privateTint.copy(alpha = NOTICE_WASH) else theme.surface
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        PanelText(text, theme, size = 12.sp, color = if (tinted) theme.privateTint else theme.hint)
    }
}

@Composable
private fun ClipRow(theme: PanelTheme, item: ClipItem, actions: ClipboardActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(theme.shape())
            .background(theme.surface)
            .clickable { actions.onPaste(item) }
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PanelText(
            item.text,
            theme,
            Modifier.weight(1f).padding(vertical = 8.dp),
            size = 14.sp,
            maxLines = 2
        )
        val pin = stringResource(if (item.pinned) R.string.panel_unpin else R.string.panel_pin)
        PanelButton(theme, pin, { actions.onTogglePin(item) }, Modifier.size(40.dp)) {
            PinIcon(if (item.pinned) theme.accent else theme.hint, item.pinned)
        }
        PanelButton(
            theme,
            stringResource(R.string.panel_delete),
            { actions.onDelete(item) },
            Modifier.size(40.dp)
        ) { TrashIcon(theme.hint) }
    }
}

private const val NOTICE_WASH = 0.18f
