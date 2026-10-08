// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.panels

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qtekfun.ultimatekeys.ime.R
import com.qtekfun.ultimatekeys.ime.surface.KeyRepeat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun PanelTheme.shape() = RoundedCornerShape(cornerDp.dp)

/** Text in the style's font. */
@Composable
internal fun PanelText(
    text: String,
    theme: PanelTheme,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    color: Color = theme.text,
    center: Boolean = false,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = theme.font,
            fontWeight = theme.fontWeight,
            textAlign = if (center) TextAlign.Center else TextAlign.Start
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/** A tappable square-ish button; [selected] fills it with the surface colour. */
@Composable
internal fun PanelButton(
    theme: PanelTheme,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(theme.shape())
            .background(if (selected) theme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
internal fun VectorIcon(@DrawableRes id: Int, tint: Color, size: Dp = 22.dp) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        modifier = Modifier.size(size),
        colorFilter = ColorFilter.tint(tint)
    )
}

/** The bottom bar: back to the keys, the two panels, and (for emoji) skin tone and backspace. */
@Composable
internal fun PanelBottomBar(
    theme: PanelTheme,
    current: PanelKind,
    tone: SkinToneButton?,
    onKeys: () -> Unit,
    onSwitch: (PanelKind) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(theme.rowDp.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val keys = stringResource(R.string.panel_back_to_keys)
        PanelButton(
            theme,
            keys,
            onKeys,
            Modifier.size(width = 52.dp, height = 40.dp),
            selected = true
        ) {
            PanelText(stringResource(R.string.panel_abc), theme, size = 14.sp)
        }
        PanelButton(
            theme,
            stringResource(R.string.panel_emoji),
            { onSwitch(PanelKind.EMOJI) },
            Modifier.size(44.dp, 40.dp),
            selected = current == PanelKind.EMOJI
        ) { VectorIcon(R.drawable.ms_sentiment_satisfied, theme.text) }
        PanelButton(
            theme,
            stringResource(R.string.panel_clipboard),
            { onSwitch(PanelKind.CLIPBOARD) },
            Modifier.size(44.dp, 40.dp),
            selected = current == PanelKind.CLIPBOARD
        ) { VectorIcon(R.drawable.ms_content_paste, theme.text) }
        Box(Modifier.weight(1f))
        if (tone != null) {
            PanelButton(
                theme,
                stringResource(R.string.panel_skin_tone),
                tone.onClick,
                Modifier.size(44.dp, 40.dp)
            ) { ToneSwatch(theme, tone.color) }
        }
        RepeatingButton(theme, stringResource(R.string.key_delete), onDelete) {
            VectorIcon(R.drawable.ms_backspace, theme.text)
        }
    }
}

/** The skin-tone button of the emoji panel: shows the tone in use as a swatch. */
internal data class SkinToneButton(val color: Color, val onClick: () -> Unit)

@Composable
private fun ToneSwatch(theme: PanelTheme, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        drawCircle(color)
        drawCircle(theme.hint, radius = size.minDimension / 2f, style = Stroke(width = 1.5f))
    }
}

/** Backspace: once on touch, then repeating while held, like the key on the keyboard. */
@Composable
private fun RepeatingButton(
    theme: PanelTheme,
    description: String,
    onTick: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val tick = rememberUpdatedState(onTick)
    Box(
        modifier = Modifier
            .size(52.dp, 40.dp)
            .clip(theme.shape())
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    tick.value()
                    val job = scope.launch {
                        delay(KeyRepeat.INITIAL_DELAY_MS)
                        var index = 0
                        while (true) {
                            repeat(KeyRepeat.charsPerTick(index)) { tick.value() }
                            delay(KeyRepeat.intervalMs(index))
                            index++
                        }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) { content() }
}

// Icons drawn here so they follow the style's colours exactly and need no extra assets.

@Composable
internal fun SearchIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * ICON_STROKE, cap = StrokeCap.Round)
        drawCircle(tint, radius = w * 0.27f, center = Offset(w * 0.42f, w * 0.42f), style = stroke)
        drawLine(
            tint,
            Offset(w * 0.62f, w * 0.62f),
            Offset(w * 0.86f, w * 0.86f),
            w * ICON_STROKE,
            StrokeCap.Round
        )
    }
}

@Composable
internal fun ClockIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val c = Offset(w / 2f, w / 2f)
        drawCircle(tint, radius = w * 0.38f, center = c, style = Stroke(width = w * ICON_STROKE))
        drawLine(tint, c, Offset(c.x, c.y - w * 0.22f), w * ICON_STROKE, StrokeCap.Round)
        drawLine(
            tint,
            c,
            Offset(c.x + w * 0.16f, c.y + w * 0.08f),
            w * ICON_STROKE,
            StrokeCap.Round
        )
    }
}

@Composable
internal fun PinIcon(tint: Color, filled: Boolean, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val head = Offset(w * 0.5f, w * 0.32f)
        if (filled) {
            drawCircle(tint, radius = w * 0.2f, center = head)
        } else {
            drawCircle(
                tint,
                radius = w * 0.2f,
                center = head,
                style = Stroke(
                    width =
                        w * ICON_STROKE
                )
            )
        }
        drawLine(
            tint,
            Offset(w * 0.5f, w * 0.52f),
            Offset(w * 0.5f, w * 0.9f),
            w * ICON_STROKE,
            StrokeCap.Round
        )
    }
}

@Composable
internal fun TrashIcon(tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * ICON_STROKE, cap = StrokeCap.Round)
        drawRoundRect(
            tint,
            Offset(w * 0.26f, w * 0.34f),
            Size(w * 0.48f, w * 0.52f),
            CornerRadius(w * 0.06f),
            style = stroke
        )
        drawLine(
            tint,
            Offset(w * 0.18f, w * 0.26f),
            Offset(w * 0.82f, w * 0.26f),
            w * ICON_STROKE,
            StrokeCap.Round
        )
        drawLine(
            tint,
            Offset(w * 0.4f, w * 0.14f),
            Offset(w * 0.6f, w * 0.14f),
            w * ICON_STROKE,
            StrokeCap.Round
        )
    }
}

private const val ICON_STROKE = 0.09f
