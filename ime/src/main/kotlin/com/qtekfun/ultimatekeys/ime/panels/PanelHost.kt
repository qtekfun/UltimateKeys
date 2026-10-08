// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatekeys.ime.KeyboardController
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.layouts.KeyAction
import com.qtekfun.ultimatekeys.style.MotionStyle
import com.qtekfun.ultimatekeys.style.PanelTransition

/**
 * Draws the open panel over the keys, with the style's transition, and wires it to the
 * [KeyboardController]. [heightDp] is the height of the keys it covers.
 */
@Composable
fun PanelHost(
    controller: KeyboardController,
    theme: PanelTheme,
    heightDp: Float,
    modifier: Modifier = Modifier
) {
    val kind by controller.panels.kind.collectAsState()
    // The panel stays drawn while it animates out, after the controller already says NONE.
    var shown by remember { mutableStateOf(PanelKind.EMOJI) }
    LaunchedEffect(kind) { if (kind != PanelKind.NONE) shown = kind }
    AnimatedVisibility(
        visible = kind != PanelKind.NONE,
        // Taps on empty parts of the panel must not reach the keys underneath.
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .pointerInput(Unit) { detectTapGestures { } },
        enter = enterFor(theme.motion),
        exit = exitFor(theme.motion)
    ) {
        when (shown) {
            PanelKind.CLIPBOARD -> ClipboardHost(controller, theme)
            else -> EmojiHost(controller, theme)
        }
    }
}

@Composable
private fun ClipboardHost(controller: KeyboardController, theme: PanelTheme) {
    val panels = controller.panels
    val items by panels.clips.collectAsState()
    val privacy by controller.privacy.state.collectAsState()
    val settings by controller.settings.collectAsState()
    val actions = remember(controller) {
        ClipboardActions(
            onPaste = panels::paste,
            onTogglePin = panels::togglePin,
            onDelete = panels::delete,
            onClearAll = panels::clearAll,
            onKeys = panels::close,
            onSwitch = panels::open,
            onDeleteText = { controller.onAction(KeyAction.DELETE) }
        )
    }
    ClipboardPanel(
        theme,
        ClipboardUi(items, privacy.isPrivate, !settings.clipboardEnabled),
        actions
    )
}

@Composable
private fun EmojiHost(controller: KeyboardController, theme: PanelTheme) {
    val panels = controller.panels
    val data by panels.emoji.collectAsState()
    val category by panels.category.collectAsState()
    val searching by panels.searching.collectAsState()
    val query by panels.query.collectAsState()
    val results by panels.results.collectAsState()
    val settings by controller.settings.collectAsState()
    val rows = remember(settings.letterLayoutId) {
        PanelLetters.rows(controller.layoutFor(Page.LETTERS, settings))
    }
    val actions = remember(controller) {
        EmojiActions(
            onSearch = panels::openSearch,
            onCloseSearch = panels::closeSearch,
            onSelectCategory = panels::selectCategory,
            onPick = panels::pick,
            onPickText = panels::pickText,
            onType = panels::typeQuery,
            onDeleteQuery = panels::deleteQueryChar,
            onCycleTone = panels::cycleSkinTone,
            onKeys = panels::close,
            onSwitch = panels::open,
            onDelete = { controller.onAction(KeyAction.DELETE) }
        )
    }
    val ui = EmojiUi(
        category = category,
        recents = panels.recents(),
        entries = category?.let { data?.catalog?.group(it) }.orEmpty(),
        tone = panels.skinTone(),
        loading = data == null,
        searching = searching,
        query = query,
        results = results,
        letterRows = rows
    )
    EmojiPanel(theme, ui, actions)
}

private fun enterFor(motion: MotionStyle): EnterTransition {
    val spec = tween<Float>(motion.durationMs)
    return when (motion.transition) {
        PanelTransition.NONE -> EnterTransition.None

        PanelTransition.FADE -> fadeIn(spec)

        PanelTransition.SLIDE -> slideInVertically(tween(motion.durationMs)) { it / 2 } +
            fadeIn(spec)
    }
}

private fun exitFor(motion: MotionStyle): ExitTransition {
    val spec = tween<Float>(motion.durationMs)
    return when (motion.transition) {
        PanelTransition.NONE -> ExitTransition.None

        PanelTransition.FADE -> fadeOut(spec)

        PanelTransition.SLIDE -> slideOutVertically(tween(motion.durationMs)) { it / 2 } +
            fadeOut(spec)
    }
}
