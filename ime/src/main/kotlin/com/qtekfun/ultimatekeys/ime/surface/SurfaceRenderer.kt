// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.qtekfun.ultimatekeys.ime.logic.EnterKind
import com.qtekfun.ultimatekeys.ime.logic.KeyboardState
import com.qtekfun.ultimatekeys.ime.logic.ShiftState
import com.qtekfun.ultimatekeys.layouts.ActionKey
import com.qtekfun.ultimatekeys.layouts.CharKey
import com.qtekfun.ultimatekeys.layouts.KeyAction
import com.qtekfun.ultimatekeys.layouts.LayoutKey
import com.qtekfun.ultimatekeys.style.BarLayout
import com.qtekfun.ultimatekeys.style.FontChoice
import com.qtekfun.ultimatekeys.style.PopupKind
import com.qtekfun.ultimatekeys.style.PressAnimation
import com.qtekfun.ultimatekeys.style.ShadowKind

/** Texts the renderer needs, resolved from resources by the composable. */
data class SurfaceLabels(
    val enter: Map<EnterKind, String>,
    val space: String,
    val symbols: String,
    val letters: String,
    val moreSymbols: String
)

/** Sizes in pixels. */
data class SurfaceDimens(
    val gapX: Float,
    val gapY: Float,
    val corner: Float,
    val borderWidth: Float,
    val elevation: Float,
    val labelSize: Float,
    val hintSize: Float,
    val previewHeight: Float
)

/** Draws the key surface with plain canvas calls: one pass, no per-key composables. */
class SurfaceRenderer(private val fonts: FontProvider = SystemFonts) {
    private var loadedFont: FontChoice? = null
    private var loadedWeight = 0
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    fun draw(
        scope: DrawScope,
        geometry: KeyGeometry,
        state: KeyboardState,
        presses: List<PressView>,
        style: SurfaceStyle,
        labels: SurfaceLabels,
        dimens: SurfaceDimens,
        strip: List<String> = emptyList()
    ) {
        if (style.font != loadedFont || style.fontWeight != loadedWeight) {
            text.typeface = fonts.typeface(style.font, style.fontWeight)
            loadedFont = style.font
            loadedWeight = style.fontWeight
        }
        scope.drawRect(style.background)
        drawStrip(scope, geometry, strip, style, dimens)
        val pressedKeys = presses.map { it.key }.toSet()
        geometry.keys.forEach { placed ->
            drawKey(scope, placed, state, placed in pressedKeys, style, labels, dimens)
        }
        presses.forEach { press ->
            val chooser = press.chooser
            if (chooser != null) {
                drawChooser(scope, chooser, style, dimens)
            } else if (press.key.key is CharKey && press.mode == PressMode.NORMAL) {
                drawPreview(scope, press.key, state, style, dimens)
            }
        }
    }

    private fun drawStrip(
        scope: DrawScope,
        geometry: KeyGeometry,
        strip: List<String>,
        style: SurfaceStyle,
        dimens: SurfaceDimens
    ) {
        if (strip.isEmpty() || geometry.top <= 0f) return
        val cell = geometry.width / strip.size
        val list = style.barLayout == BarLayout.SCROLLING_LIST
        strip.forEachIndexed { index, word ->
            if (list) {
                val pad = geometry.top * PILL_PAD
                val height = geometry.top * PILL_HEIGHT
                scope.drawRoundRect(
                    style.barDivider.copy(alpha = PILL_ALPHA),
                    Offset(cell * index + pad, (geometry.top - height) / 2f),
                    Size(cell - 2f * pad, height),
                    CornerRadius(height / 2f)
                )
            } else if (index > 0) {
                scope.drawLine(
                    style.barDivider,
                    Offset(cell * index, geometry.top * DIVIDER_INSET),
                    Offset(cell * index, geometry.top * (1f - DIVIDER_INSET)),
                    1f
                )
            }
            text.isFakeBoldText = (!list && index == 1) || style.barTextBold
            val baseline = geometry.top / 2f + dimens.labelSize * STRIP_BASELINE
            drawText(
                scope,
                word,
                cell * index + cell / 2f,
                baseline,
                dimens.labelSize * STRIP_TEXT * style.barTextSizePercent / PERCENT,
                style.barText
            )
            text.isFakeBoldText = false
        }
    }

    private fun drawKey(
        scope: DrawScope,
        placed: PlacedKey,
        state: KeyboardState,
        pressed: Boolean,
        style: SurfaceStyle,
        labels: SurfaceLabels,
        dimens: SurfaceDimens
    ) {
        val key = placed.key
        val isEnter = key is ActionKey && key.action == KeyAction.ENTER
        val fill = keyFill(key, pressed, style)
        val shrink = if (pressed && style.pressAnimation == PressAnimation.SCALE) {
            placed.height * PRESS_SHRINK
        } else {
            0f
        }
        val topLeft = Offset(placed.left + shrink, placed.top + shrink)
        val size = Size(placed.width - 2f * shrink, placed.height - 2f * shrink)
        val radius = CornerRadius(dimens.corner)
        drawShadow(scope, topLeft, size, radius, style, dimens)
        scope.drawRoundRect(color = fill, topLeft = topLeft, size = size, cornerRadius = radius)
        if (style.hasBorder) {
            scope.drawRoundRect(
                color = style.keyBorder,
                topLeft = topLeft,
                size = size,
                cornerRadius = radius,
                style = Stroke(width = dimens.borderWidth)
            )
        }
        val ink = if (isEnter) style.actionText else style.text
        when (key) {
            is CharKey -> drawCharKey(scope, placed, key, state, style, dimens)
            is ActionKey -> drawActionKey(scope, placed, key, state, ink, labels, dimens)
        }
    }

    private fun keyFill(key: LayoutKey, pressed: Boolean, style: SurfaceStyle): Color {
        val base = when {
            key is ActionKey && key.action == KeyAction.ENTER -> style.actionKey
            key is ActionKey && key.action == KeyAction.SPACE -> style.spaceKey
            key is CharKey -> style.letterKey
            else -> style.functionKey
        }
        return if (pressed && style.pressAnimation != PressAnimation.SCALE) style.pressed else base
    }

    private fun drawShadow(
        scope: DrawScope,
        topLeft: Offset,
        size: Size,
        radius: CornerRadius,
        style: SurfaceStyle,
        dimens: SurfaceDimens
    ) {
        when (style.shadow) {
            ShadowKind.NONE -> Unit

            ShadowKind.BOTTOM_EDGE -> scope.drawRoundRect(
                style.keyShadow,
                Offset(topLeft.x, topLeft.y + dimens.elevation),
                size,
                radius
            )

            ShadowKind.ELEVATION -> repeat(ELEVATION_LAYERS) { layer ->
                val spread = dimens.elevation * (layer + 1) / ELEVATION_LAYERS
                scope.drawRoundRect(
                    style.keyShadow.copy(alpha = style.keyShadow.alpha / (layer + 2)),
                    Offset(topLeft.x - spread / 2f, topLeft.y + spread / 2f),
                    Size(size.width + spread, size.height + spread),
                    CornerRadius(radius.x + spread / 2f)
                )
            }
        }
    }

    private fun drawCharKey(
        scope: DrawScope,
        placed: PlacedKey,
        key: CharKey,
        state: KeyboardState,
        style: SurfaceStyle,
        dimens: SurfaceDimens
    ) {
        val label = if (upperCase(state, style)) key.label.uppercase() else key.label
        drawText(
            scope,
            label,
            placed.centerX,
            placed.top + placed.height / 2f + dimens.labelSize / 3f,
            dimens.labelSize,
            style.text
        )
        key.hint?.takeIf { style.showHints }?.let {
            drawText(
                scope,
                it,
                placed.right - dimens.hintSize,
                placed.top + dimens.hintSize * 1.2f,
                dimens.hintSize,
                style.hint
            )
        }
    }

    private fun drawActionKey(
        scope: DrawScope,
        placed: PlacedKey,
        key: ActionKey,
        state: KeyboardState,
        ink: Color,
        labels: SurfaceLabels,
        dimens: SurfaceDimens
    ) {
        val cx = placed.centerX
        val cy = placed.top + placed.height / 2f
        val unit = minOf(placed.height, placed.width) * ICON_SCALE
        val small = dimens.labelSize * SMALL_LABEL
        when (key.action) {
            KeyAction.SHIFT -> KeyIcons.shift(scope, cx, cy, unit, ink, state.shift)

            KeyAction.DELETE -> KeyIcons.backspace(scope, cx, cy, unit, ink)

            KeyAction.ENTER -> drawEnter(scope, placed, state.enterKind, ink, labels, dimens, unit)

            KeyAction.SPACE -> drawText(
                scope,
                labels.space,
                cx,
                cy + small / 3f,
                small,
                ink.copy(alpha = SPACE_ALPHA)
            )

            KeyAction.GLOBE -> KeyIcons.globe(scope, cx, cy, unit, ink)

            KeyAction.EMOJI -> KeyIcons.smiley(scope, cx, cy, unit, ink)

            KeyAction.MIC -> KeyIcons.mic(scope, cx, cy, unit, ink)

            KeyAction.SWITCH_LETTERS -> drawText(
                scope,
                key.label ?: labels.letters,
                cx,
                cy + small / 3f,
                small,
                ink
            )

            KeyAction.SWITCH_SYMBOLS -> drawText(
                scope,
                key.label ?: labels.symbols,
                cx,
                cy + small / 3f,
                small,
                ink
            )

            KeyAction.SWITCH_SYMBOLS_2 -> drawText(
                scope,
                key.label ?: labels.moreSymbols,
                cx,
                cy + small / 3f,
                small,
                ink
            )
        }
    }

    private fun drawEnter(
        scope: DrawScope,
        placed: PlacedKey,
        kind: EnterKind,
        ink: Color,
        labels: SurfaceLabels,
        dimens: SurfaceDimens,
        unit: Float
    ) {
        val cx = placed.centerX
        val cy = placed.top + placed.height / 2f
        if (kind == EnterKind.ENTER) {
            val p = Path().apply {
                moveTo(cx + unit * 0.5f, cy - unit * 0.5f)
                lineTo(cx + unit * 0.5f, cy)
                lineTo(cx - unit * 0.5f, cy)
                moveTo(cx - unit * 0.1f, cy - unit * 0.4f)
                lineTo(cx - unit * 0.5f, cy)
                lineTo(cx - unit * 0.1f, cy + unit * 0.4f)
            }
            scope.drawPath(p, ink, style = Stroke(width = unit * STROKE))
        } else {
            val size = dimens.labelSize * SMALL_LABEL
            drawText(scope, labels.enter.getValue(kind), cx, cy + size / 3f, size, ink)
        }
    }

    private fun drawPreview(
        scope: DrawScope,
        placed: PlacedKey,
        state: KeyboardState,
        style: SurfaceStyle,
        dimens: SurfaceDimens
    ) {
        val key = placed.key as CharKey
        if (style.popupKind == PopupKind.NONE) return
        val label = if (upperCase(state, style)) key.label.uppercase() else key.label
        val enlarged = style.popupKind == PopupKind.ENLARGED_KEY
        val h = if (enlarged) placed.height else dimens.previewHeight
        val w = if (enlarged) placed.width * ENLARGED_WIDEN else placed.width * PREVIEW_WIDEN
        val left = (placed.centerX - w / 2f).coerceAtLeast(0f)
        val top = (placed.top - h - dimens.gapY).coerceAtLeast(0f)
        scope.drawRoundRect(style.popup, Offset(left, top), Size(w, h), CornerRadius(dimens.corner))
        drawText(
            scope,
            label,
            left + w / 2f,
            top + h / 2f + dimens.labelSize * 0.45f,
            dimens.labelSize * PREVIEW_TEXT,
            style.popupText
        )
    }

    private fun drawChooser(
        scope: DrawScope,
        chooser: ChooserView,
        style: SurfaceStyle,
        dimens: SurfaceDimens
    ) {
        scope.drawRoundRect(
            style.popup,
            Offset(chooser.left, chooser.top),
            Size(chooser.right - chooser.left, chooser.cellHeight),
            CornerRadius(dimens.corner)
        )
        chooser.items.forEachIndexed { index, item ->
            val x = chooser.left + index * chooser.cellWidth
            if (index == chooser.selected) {
                scope.drawRoundRect(
                    style.popupSelected,
                    Offset(x + 2f, chooser.top + 2f),
                    Size(chooser.cellWidth - 4f, chooser.cellHeight - 4f),
                    CornerRadius(dimens.corner)
                )
            }
            val ink = if (index == chooser.selected) style.actionText else style.popupText
            drawText(
                scope,
                item,
                x + chooser.cellWidth / 2f,
                chooser.top + chooser.cellHeight / 2f + dimens.labelSize / 3f,
                dimens.labelSize,
                ink
            )
        }
    }

    private fun upperCase(state: KeyboardState, style: SurfaceStyle): Boolean =
        state.page == com.qtekfun.ultimatekeys.ime.logic.Page.LETTERS &&
            (style.alwaysUpper || state.shift != ShiftState.OFF)

    private fun drawText(
        scope: DrawScope,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Color
    ) {
        text.textSize = size
        text.color = color.toArgb()
        scope.drawIntoCanvas { it.nativeCanvas.drawText(value, x, baseline, text) }
    }

    private companion object {
        const val ICON_SCALE = 0.4f
        const val DIVIDER_ALPHA = 0.4f
        const val DIVIDER_INSET = 0.25f
        const val STRIP_TEXT = 0.9f
        const val STRIP_BASELINE = 0.35f
        const val SMALL_LABEL = 0.7f
        const val SPACE_ALPHA = 0.6f
        const val STROKE = 0.1f
        const val PREVIEW_WIDEN = 1.3f
        const val ENLARGED_WIDEN = 1.25f
        const val PRESS_SHRINK = 0.04f
        const val ELEVATION_LAYERS = 3
        const val PERCENT = 100f
        const val PILL_PAD = 0.12f
        const val PILL_HEIGHT = 0.68f
        const val PILL_ALPHA = 0.55f
        const val PREVIEW_TEXT = 1.3f
    }
}
