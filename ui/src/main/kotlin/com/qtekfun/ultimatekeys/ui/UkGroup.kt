// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("TooManyFunctions")

package com.qtekfun.ultimatekeys.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Collects the rows of a [UkGroup]; the group draws the separators between them. */
class UkGroupScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    /** Adds one row, any composable. Rows added under an `if` simply appear and disappear. */
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/** Where the separators of a group start: under the text, so they skip the leading icon badge. */
object UkSeparatorInset {
    val Plain: Dp = UkSpacing.md
    val WithBadge: Dp = UkSpacing.md + UkSize.badge + UkSpacing.md
}

/**
 * A group of rows on one rounded card over the tinted page. [header] is the small label above it
 * and [footer] the explanation below. Hairline separators, inset from the leading edge by
 * [separatorInset], sit between rows (never after the last).
 */
@Composable
fun UkGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    separatorInset: Dp = UkSeparatorInset.Plain,
    content: UkGroupScope.() -> Unit
) {
    val colors = UkTheme.colors
    val rows = UkGroupScope().apply(content).rows
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = UkSpacing.md)
            .padding(top = UkSpacing.lg)
    ) {
        if (header != null) GroupHeader(header)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(UkRadius.card))
                .background(colors.card)
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) UkSeparator(separatorInset)
                row()
            }
        }
        if (footer != null) GroupFooter(footer)
    }
}

/**
 * A long group whose rows are composed lazily (the user's words may be hundreds): the rows of
 * [items] share one rounded card, drawn piece by piece, with the same header, footer and
 * separators as [UkGroup].
 */
fun <T> LazyListScope.groupRows(
    items: List<T>,
    key: (T) -> Any,
    header: String? = null,
    footer: String? = null,
    separatorInset: Dp = UkSeparatorInset.Plain,
    row: @Composable (T) -> Unit
) {
    if (items.isEmpty()) return
    item(key = "group-header-${key(items.first())}") {
        Column {
            Spacer(Modifier.height(UkSpacing.lg))
            if (header != null) GroupHeader(header, Modifier.padding(horizontal = UkSpacing.md))
        }
    }
    itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
        val top = if (index == 0) UkRadius.card else 0.dp
        val bottom = if (index == items.lastIndex) UkRadius.card else 0.dp
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = UkSpacing.md)
                .clip(RoundedCornerShape(top, top, bottom, bottom))
                .background(UkTheme.colors.card)
        ) {
            if (index > 0) UkSeparator(separatorInset)
            row(item)
        }
    }
    if (footer != null) {
        item(key = "group-footer-${key(items.first())}") {
            GroupFooter(footer, Modifier.padding(horizontal = UkSpacing.md))
        }
    }
}

/** A paragraph of explanation between groups, in the footer style. */
fun LazyListScope.note(text: String, key: Any? = null) {
    item(key = key) { GroupFooter(text, Modifier.padding(horizontal = UkSpacing.md)) }
}

@Composable
private fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(LocalLocale.current.platformLocale),
        modifier = modifier
            .padding(start = UkSpacing.md, bottom = UkSpacing.sm)
            .semantics { heading() },
        style = UkTheme.typography.sectionHeader,
        color = UkTheme.colors.secondaryLabel
    )
}

@Composable
private fun GroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = UkSpacing.md, vertical = UkSpacing.sm),
        style = UkTheme.typography.footnote,
        color = UkTheme.colors.secondaryLabel
    )
}

/**
 * A [UkGroup] as one item of a screen's list whose header and footer are string resources (a
 * list builder is not composable, so it cannot look the text up itself).
 */
fun LazyListScope.section(
    @StringRes header: Int? = null,
    @StringRes footer: Int? = null,
    key: Any? = null,
    separatorInset: Dp = UkSeparatorInset.Plain,
    content: UkGroupScope.() -> Unit
) {
    item(key = key) {
        UkGroup(
            header = header?.let { stringResource(it) },
            footer = footer?.let { stringResource(it) },
            separatorInset = separatorInset,
            content = content
        )
    }
}

/** A [UkGroup] as one item of a screen's list. */
fun LazyListScope.group(
    key: Any? = null,
    header: String? = null,
    footer: String? = null,
    separatorInset: Dp = UkSeparatorInset.Plain,
    content: UkGroupScope.() -> Unit
) {
    item(key = key) {
        UkGroup(
            header = header,
            footer = footer,
            separatorInset = separatorInset,
            content = content
        )
    }
}

@Composable
private fun UkSeparator(inset: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(UkSize.hairline)
            .background(UkTheme.colors.separator)
    )
}

/** The content of a row: optional leading item, title (+ subtitle) and a trailing area. */
@Composable
fun UkRowContent(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    destructive: Boolean = false,
    titleColor: androidx.compose.ui.graphics.Color? = null,
    enabled: Boolean = true
) {
    val colors = UkTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = UkSize.rowMinHeight)
            .padding(horizontal = UkSpacing.md, vertical = UkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(UkSpacing.md))
        }
        Column(Modifier.weight(1f)) {
            val color = when {
                !enabled -> colors.secondaryLabel
                destructive -> colors.destructive
                else -> titleColor ?: colors.label
            }
            Text(title, style = UkTheme.typography.body, color = color)
            if (subtitle != null) {
                Text(subtitle, style = UkTheme.typography.footnote, color = colors.secondaryLabel)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(UkSpacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(UkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing
            )
        }
    }
}

/** A row that does something when tapped: one focus stop for a screen reader, role button. */
@Composable
fun UkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    destructive: Boolean = false,
    titleColor: androidx.compose.ui.graphics.Color? = null,
    enabled: Boolean = true,
    role: Role = Role.Button,
    state: String? = null,
    onClickLabel: String? = null
) {
    UkRowContent(
        title = title,
        modifier = modifier
            .clickable(
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = role,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) { state?.let { stateDescription = it } },
        subtitle = subtitle,
        leading = leading,
        trailing = trailing,
        destructive = destructive,
        titleColor = titleColor,
        enabled = enabled
    )
}

/** A row that opens a sub-screen or another screen: optional current [value] and a chevron. */
@Composable
fun UkNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    glyph: UkGlyph? = null,
    accent: BadgeAccent = BadgeAccent.Primary,
    enabled: Boolean = true,
    onClickLabel: String? = null
) {
    UkRow(
        title = title,
        onClick = onClick,
        modifier = modifier,
        onClickLabel = onClickLabel,
        subtitle = subtitle,
        leading = glyph?.let { { UkBadge(it, accent) } },
        trailing = {
            if (value != null) UkValueText(value)
            UkChevron()
        },
        enabled = enabled
    )
}

/** A read-only row: a title and a value, no action. One focus stop reading "title, value". */
@Composable
fun UkValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    UkRowContent(
        title = title,
        modifier = modifier.semantics(mergeDescendants = true) {},
        subtitle = subtitle,
        trailing = { UkValueText(value) }
    )
}

/** A tinted action in a group ("Clear learned data"); [destructive] draws it in the error colour. */
@Composable
fun UkActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    UkRow(
        title = title,
        onClick = onClick,
        modifier = modifier,
        destructive = destructive,
        titleColor = UkTheme.colors.tint,
        enabled = enabled
    )
}

/** A row with a whole-row switch: the label and the switch are one focus stop and one target. */
@Composable
fun UkSwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    UkRowContent(
        title = title,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onChange
        ),
        subtitle = subtitle,
        trailing = { UkSwitch(checked, enabled = enabled) },
        enabled = enabled
    )
}

/** Free-form content inside a group (a card of a model, a preview, a text field). */
@Composable
fun UkContentRow(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = UkSpacing.md, vertical = UkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(UkSpacing.sm),
        content = content
    )
}

@Composable
internal fun UkValueText(text: String) {
    Text(
        text,
        modifier = Modifier.widthIn(max = VALUE_MAX_WIDTH),
        style = UkTheme.typography.body,
        color = UkTheme.colors.secondaryLabel,
        textAlign = TextAlign.End,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

private val VALUE_MAX_WIDTH = 180.dp

/** The disclosure arrow at the end of a row that navigates. Decorative. */
@Composable
fun UkChevron(modifier: Modifier = Modifier, pointingDown: Boolean = false) {
    UkIcon(
        UkGlyph.Chevron,
        UkTheme.colors.secondaryLabel,
        modifier.rotate(if (pointingDown) DOWN_DEGREES else 0f),
        size = UkSize.chevron
    )
}

private const val DOWN_DEGREES = 90f

/** The rounded-square icon of a home row, coloured by [accent]. Decorative. */
@Composable
fun UkBadge(glyph: UkGlyph, accent: BadgeAccent, modifier: Modifier = Modifier) {
    val (container, content) = UkTheme.colors.badges.getValue(accent)
    Box(
        modifier
            .size(UkSize.badge)
            .clip(RoundedCornerShape(UkRadius.badge))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        UkIcon(glyph, content, size = UkSize.badgeGlyph)
    }
}
