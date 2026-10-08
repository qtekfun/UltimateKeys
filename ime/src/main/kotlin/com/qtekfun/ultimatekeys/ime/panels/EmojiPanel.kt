// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("MagicNumber", "LongParameterList", "TooManyFunctions", "MatchingDeclarationName")

package com.qtekfun.ultimatekeys.ime.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qtekfun.ultimatekeys.emoji.EmojiCategory
import com.qtekfun.ultimatekeys.emoji.EmojiEntry
import com.qtekfun.ultimatekeys.emoji.EmojiHit
import com.qtekfun.ultimatekeys.emoji.SkinTone
import com.qtekfun.ultimatekeys.ime.R
import com.qtekfun.ultimatekeys.layouts.CharKey

/** Everything the emoji panel shows; the stateful host builds it from [PanelsController]. */
internal data class EmojiUi(
    /** The tab on show; null is the "recent" tab. */
    val category: EmojiCategory?,
    val recents: List<String>,
    val entries: List<EmojiEntry>,
    val tone: SkinTone,
    val loading: Boolean,
    val searching: Boolean,
    val query: String,
    val results: List<EmojiHit>,
    /** The letter rows of the search keyboard. */
    val letterRows: List<List<CharKey>>
)

internal class EmojiActions(
    val onSearch: () -> Unit,
    val onCloseSearch: () -> Unit,
    val onSelectCategory: (EmojiCategory?) -> Unit,
    val onPick: (EmojiEntry) -> Unit,
    val onPickText: (String) -> Unit,
    val onType: (String) -> Unit,
    val onDeleteQuery: () -> Unit,
    val onCycleTone: () -> Unit,
    val onKeys: () -> Unit,
    val onSwitch: (PanelKind) -> Unit,
    val onDelete: () -> Unit
)

@Composable
internal fun EmojiPanel(
    theme: PanelTheme,
    ui: EmojiUi,
    actions: EmojiActions,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(theme.background)) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            if (ui.searching) {
                SearchContent(theme, ui, actions)
            } else {
                BrowseContent(theme, ui, actions)
            }
        }
        PanelBottomBar(
            theme = theme,
            current = PanelKind.EMOJI,
            tone = SkinToneButton(toneColor(ui.tone), actions.onCycleTone),
            onKeys = actions.onKeys,
            onSwitch = actions.onSwitch,
            onDelete = actions.onDelete
        )
    }
}

@Composable
private fun ColumnScope.BrowseContent(theme: PanelTheme, ui: EmojiUi, actions: EmojiActions) {
    Row(
        Modifier.fillMaxWidth().height(theme.barDp.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        PanelButton(
            theme,
            stringResource(R.string.panel_search_emoji),
            actions.onSearch,
            Modifier.size(40.dp)
        ) { SearchIcon(theme.text) }
        LazyRow(
            Modifier.weight(1f).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                PanelButton(
                    theme,
                    stringResource(R.string.panel_recents),
                    { actions.onSelectCategory(null) },
                    Modifier.size(40.dp),
                    selected = ui.category == null
                ) { ClockIcon(theme.text) }
            }
            items(EmojiCategory.entries) { category ->
                PanelButton(
                    theme,
                    categoryName(category),
                    { actions.onSelectCategory(category) },
                    Modifier.size(40.dp),
                    selected = ui.category == category
                ) { EmojiText(category.icon, 22.sp) }
            }
        }
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
        val cells = if (ui.category == null) ui.recents else ui.entries.map { it.forTone(ui.tone) }
        when {
            cells.isNotEmpty() -> EmojiGrid(cells, actions, ui)
            ui.category == null -> Message(theme, stringResource(R.string.panel_recents_empty))
            else -> Message(theme, stringResource(R.string.panel_loading))
        }
    }
}

@Composable
private fun EmojiGrid(cells: List<String>, actions: EmojiActions, ui: EmojiUi) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 44.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)
    ) {
        items(cells) { emoji ->
            Box(
                Modifier
                    .height(44.dp)
                    .clickable { pick(emoji, ui, actions) },
                contentAlignment = Alignment.Center
            ) { EmojiText(emoji, 26.sp) }
        }
    }
}

/** A tap on a cell: a category emoji keeps its tone form; recents are inserted as they are. */
private fun pick(emoji: String, ui: EmojiUi, actions: EmojiActions) {
    if (ui.category == null) {
        actions.onPickText(emoji)
    } else {
        actions.onPick(ui.entries.first { it.forTone(ui.tone) == emoji })
    }
}

@Composable
private fun ColumnScope.SearchContent(theme: PanelTheme, ui: EmojiUi, actions: EmojiActions) {
    Row(
        Modifier.fillMaxWidth().height(theme.barDp.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(theme.barDp.dp - 8.dp)
                .clip(theme.shape())
                .background(theme.surface)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchIcon(theme.hint, 18.dp)
            if (ui.query.isEmpty()) {
                PanelText(stringResource(R.string.panel_search_emoji), theme, color = theme.hint)
            } else {
                PanelText(ui.query, theme, maxLines = 1)
            }
        }
        PanelButton(
            theme,
            stringResource(R.string.panel_close_search),
            actions.onCloseSearch,
            Modifier.size(40.dp)
        ) { VectorIcon(R.drawable.ms_close, theme.text) }
    }
    Box(Modifier.fillMaxWidth().height(SEARCH_RESULT_DP.dp)) {
        when {
            ui.query.isBlank() ->
                Message(theme, stringResource(R.string.panel_search_hint))

            ui.loading && ui.results.isEmpty() ->
                Message(theme, stringResource(R.string.panel_loading))

            ui.results.isEmpty() ->
                Message(theme, stringResource(R.string.panel_no_emoji))

            else -> LazyRow(
                Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(ui.results) { hit ->
                    Box(
                        Modifier
                            .size(SEARCH_RESULT_DP.dp)
                            .semantics { contentDescription = hit.name }
                            .clickable { actions.onPickText(hit.emoji) },
                        contentAlignment = Alignment.Center
                    ) { EmojiText(hit.emoji, 28.sp) }
                }
            }
        }
    }
    SearchKeys(theme, ui.letterRows, actions, Modifier.weight(1f))
}

/** A small letter keyboard: the panel replaces the keys, so searching needs its own. */
@Composable
private fun SearchKeys(
    theme: PanelTheme,
    rows: List<List<CharKey>>,
    actions: EmojiActions,
    modifier: Modifier
) {
    val deleteLabel = stringResource(R.string.key_delete)
    Column(modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp)) {
        rows.forEachIndexed { index, keys ->
            val last = index == rows.lastIndex
            Row(Modifier.weight(1f).fillMaxWidth()) {
                keys.forEach { key ->
                    Box(
                        Modifier
                            .weight(key.width)
                            .fillMaxHeight()
                            .padding(2.dp)
                            .clip(theme.shape())
                            .background(theme.surface)
                            .clickable { actions.onType(key.output) },
                        contentAlignment = Alignment.Center
                    ) { PanelText(key.label, theme, size = 17.sp, center = true) }
                }
                if (last) {
                    Box(
                        Modifier
                            .weight(1.5f)
                            .fillMaxHeight()
                            .padding(2.dp)
                            .clip(theme.shape())
                            .background(theme.surface)
                            .clickable(onClick = actions.onDeleteQuery)
                            .semantics { contentDescription = deleteLabel },
                        contentAlignment = Alignment.Center
                    ) { VectorIcon(R.drawable.ms_backspace, theme.text) }
                }
            }
        }
    }
}

@Composable
internal fun Message(theme: PanelTheme, text: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        PanelText(text, theme, color = theme.hint, center = true)
    }
}

@Composable
private fun EmojiText(emoji: String, size: TextUnit) {
    BasicText(emoji, style = TextStyle(fontSize = size, textAlign = TextAlign.Center))
}

@Composable
private fun categoryName(category: EmojiCategory): String = stringResource(
    when (category) {
        EmojiCategory.SMILEYS -> R.string.panel_cat_smileys
        EmojiCategory.PEOPLE -> R.string.panel_cat_people
        EmojiCategory.ANIMALS -> R.string.panel_cat_animals
        EmojiCategory.FOOD -> R.string.panel_cat_food
        EmojiCategory.TRAVEL -> R.string.panel_cat_travel
        EmojiCategory.ACTIVITIES -> R.string.panel_cat_activities
        EmojiCategory.OBJECTS -> R.string.panel_cat_objects
        EmojiCategory.SYMBOLS -> R.string.panel_cat_symbols
        EmojiCategory.FLAGS -> R.string.panel_cat_flags
    }
)

internal fun toneColor(tone: SkinTone): Color = when (tone) {
    SkinTone.NONE -> Color(0xFFFFC83D)
    SkinTone.LIGHT -> Color(0xFFF7DCC0)
    SkinTone.MEDIUM_LIGHT -> Color(0xFFE6BC94)
    SkinTone.MEDIUM -> Color(0xFFC48E5E)
    SkinTone.MEDIUM_DARK -> Color(0xFF8D5A3B)
    SkinTone.DARK -> Color(0xFF4F3326)
}

private const val SEARCH_RESULT_DP = 48
