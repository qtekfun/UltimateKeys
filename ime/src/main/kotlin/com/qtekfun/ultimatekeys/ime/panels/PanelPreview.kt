// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatekeys.clipboard.ClipItem
import com.qtekfun.ultimatekeys.emoji.EmojiCategory
import com.qtekfun.ultimatekeys.emoji.EmojiEntry
import com.qtekfun.ultimatekeys.emoji.EmojiHit
import com.qtekfun.ultimatekeys.emoji.SkinTone
import com.qtekfun.ultimatekeys.ime.surface.FontCatalog
import com.qtekfun.ultimatekeys.ime.surface.SurfaceSpec
import com.qtekfun.ultimatekeys.ime.surface.SurfaceStyle
import com.qtekfun.ultimatekeys.layouts.LayoutRepository
import com.qtekfun.ultimatekeys.style.Style

/** What a non-interactive panel preview shows. */
data class PanelPreviewContent(
    val kind: PanelKind = PanelKind.EMOJI,
    /** Emoji only: show the search state instead of the grid. */
    val searching: Boolean = false,
    /** Clipboard only. */
    val isPrivate: Boolean = false,
    val heightPercent: Int = 100
)

/**
 * A panel drawn with [style] and sample content, the same composables as the real panels, so the
 * screenshot tests show what people see. Not interactive.
 */
@Composable
fun PanelPreview(
    style: Style,
    dark: Boolean,
    content: PanelPreviewContent,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fonts = remember(context) { FontCatalog(context.assets) }
    val rowDp = SurfaceSpec.rowDp(content.heightPercent)
    val theme = remember(style, dark, fonts, rowDp) {
        PanelTheme.resolve(style, dark, null, fonts, rowDp)
    }
    val surface = remember(style, dark) { SurfaceStyle.resolve(style, dark) }
    val rows = LayoutRepository.pages("en_qwerty", false).letters.rows.size
    val height = SurfaceSpec.totalHeightDp(rows, content.heightPercent, 0, style)
    Box(modifier.fillMaxWidth().height(height.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) { drawRect(surface.background) }
        when (content.kind) {
            PanelKind.CLIPBOARD -> ClipboardPanel(
                theme,
                ClipboardUi(SampleClips, content.isPrivate, historyOff = false),
                NoClipboardActions
            )

            else -> EmojiPanel(theme, sampleEmojiUi(content.searching), NoEmojiActions)
        }
    }
}

private val SampleClips = listOf(
    ClipItem(1, "Meet at the station at 18:30, platform 2", pinned = true, copiedAt = 5),
    ClipItem(2, "https://example.org/invoice/2026-10", pinned = false, copiedAt = 4),
    ClipItem(3, "Calle de Alcalá 42, 3º B, 28014 Madrid", pinned = false, copiedAt = 3),
    ClipItem(4, "ES76 2077 0024 0031 0257 5766", pinned = false, copiedAt = 2)
)

private fun sampleEmojiUi(searching: Boolean): EmojiUi {
    val faces = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅",
        "🤣", "😂", "🙂", "😉", "😊", "😇",
        "😍", "😘", "😋", "😜", "🤔", "😐",
        "😎", "😭", "😡", "😱", "🥳", "😴"
    )
    return EmojiUi(
        category = EmojiCategory.SMILEYS,
        recents = faces.take(6),
        entries = faces.map { EmojiEntry(it) },
        tone = SkinTone.MEDIUM,
        loading = false,
        searching = searching,
        query = if (searching) "cora" else "",
        results = listOf("❤️", "🧡", "💛", "💚", "💙")
            .map { EmojiHit(it, "heart", 0) },
        letterRows = LayoutRepository.pages("en_qwerty", false).letters.let(PanelLetters::rows)
    )
}

private val NoClipboardActions = ClipboardActions({}, {}, {}, {}, {}, {}, {})

private val NoEmojiActions = EmojiActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
