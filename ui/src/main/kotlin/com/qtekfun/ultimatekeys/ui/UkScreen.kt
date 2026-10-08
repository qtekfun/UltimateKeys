// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Widest the content grows on a tablet or in landscape; the page colour still fills the screen. */
private val ContentMaxWidth = 640.dp

/**
 * A full screen of grouped settings: a navigation bar, a large title that collapses into it as the
 * list scrolls, and the list. The page respects [insets]: the bar sits below the status bar, the
 * list stops above the on-screen keyboard and its last row clears the navigation bar. [pinned]
 * stays fixed between the bar and the list (the live keyboard preview of the style editor);
 * a screen with something pinned usually sets [largeTitle] to false and keeps the bar title.
 */
@Composable
fun UkScreen(
    title: String,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = ScreenInsets.current(),
    onBack: (() -> Unit)? = null,
    backText: String? = null,
    backDescription: String = "",
    actions: @Composable RowScope.() -> Unit = {},
    pinned: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    largeTitle: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    var titleHeightPx by remember { mutableFloatStateOf(0f) }
    val scrollPx by remember {
        derivedStateOf {
            // Once the title item has left the list the collapse is complete.
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                Float.MAX_VALUE
            }
        }
    }
    // Without a large title the bar title is always shown, over a hairline.
    val fraction = if (largeTitle) TitleCollapse.fraction(scrollPx, titleHeightPx) else 1f
    val bottomBars = insets.bars.exclude(insets.ime).only(WindowInsetsSides.Bottom)

    Box(modifier.fillMaxSize().background(UkTheme.colors.page)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets.bars.only(WindowInsetsSides.Horizontal)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UkNavBar(
                title = title,
                collapse = fraction,
                onBack = onBack,
                backText = backText,
                backDescription = backDescription,
                actions = actions,
                modifier = Modifier.windowInsetsPadding(insets.bars.only(WindowInsetsSides.Top))
            )
            pinned?.invoke()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .windowInsetsPadding(insets.ime.only(WindowInsetsSides.Bottom)),
                contentPadding = PaddingValues(
                    bottom = bottomBars.asPaddingValues().calculateBottomPadding() + UkSpacing.lg
                )
            ) {
                if (largeTitle) {
                    item(key = "large-title") {
                        LargeTitle(
                            title,
                            Modifier.onSizeChanged { titleHeightPx = it.height.toFloat() },
                            fraction
                        )
                    }
                }
                content()
            }
        }
    }
}

@Composable
private fun LargeTitle(title: String, modifier: Modifier, collapse: Float) {
    Text(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .alpha(TitleCollapse.largeAlpha(collapse))
            .padding(horizontal = UkSpacing.md + UkSpacing.xs, vertical = UkSpacing.sm)
            .semantics { heading() },
        style = UkTheme.typography.largeTitle,
        color = UkTheme.colors.label
    )
}

/**
 * The bar at the top: a back button on the leading side, the small [title] that appears as the
 * large one scrolls away ([collapse] is 0..1), and [actions] on the trailing side. The back
 * button is a 48 dp target named by [backDescription].
 */
@Composable
fun UkNavBar(
    title: String,
    collapse: Float,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backText: String? = null,
    backDescription: String = "",
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = UkTheme.colors
    Box(modifier.fillMaxWidth().background(colors.page)) {
        Row(
            Modifier.fillMaxWidth().height(UkSize.navBarHeight).padding(horizontal = UkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (onBack != null) BackButton(onBack, backText, backDescription)
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(TITLE_WEIGHT)
                    .alpha(TitleCollapse.smallAlpha(collapse))
                    // The large title is the heading; this one only repeats it for the eye.
                    .clearAndSetSemantics { },
                style = UkTheme.typography.navTitle,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(UkSize.hairline)
                .alpha(TitleCollapse.separatorAlpha(collapse))
                .background(colors.separator)
        )
    }
}

private const val TITLE_WEIGHT = 1.5f

@Composable
private fun BackButton(onClick: () -> Unit, text: String?, description: String) {
    val tint = UkTheme.colors.tint
    Row(
        Modifier
            .heightIn(min = UkSize.minTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = UkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UkIcon(UkGlyph.Back, tint, size = BACK_ICON)
        if (text != null) {
            Text(
                text,
                style = UkTheme.typography.body,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val BACK_ICON = 26.dp

/** A text button for the bar ("Save"): tinted, at least 48 dp to hit, dimmed when disabled. */
@Composable
fun UkBarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bold: Boolean = true
) {
    val tint = UkTheme.colors.tint
    Box(
        modifier
            .heightIn(min = UkSize.minTouch)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = UkSpacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = if (bold) UkTheme.typography.navTitle else UkTheme.typography.body,
            color = if (enabled) {
                tint
            } else {
                UkTheme.colors.secondaryLabel.copy(
                    alpha = DISABLED_ALPHA
                )
            }
        )
    }
}

internal const val DISABLED_ALPHA = 0.5f
