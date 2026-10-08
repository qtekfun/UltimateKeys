// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The small icons of the app screens, drawn here as simple strokes on a 24 x 24 grid (original
 * artwork, so no icon font or library is needed). They are decorative: a row's text says the same.
 */
enum class UkGlyph {
    Chevron,
    Back,
    Check,
    Setup,
    Keyboard,
    Suggestions,
    Gestures,
    Feedback,
    Palette,
    Mic,
    Clipboard,
    Lock,
    Info
}

private const val GRID = 24f
private const val STROKE = 1.8f

/** Draws [glyph] in [tint] at [size]; it has no semantics of its own. */
@Composable
fun UkIcon(glyph: UkGlyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        scale(this.size.minDimension / GRID, pivot = Offset.Zero) { drawGlyph(glyph, tint) }
    }
}

private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float) =
    drawLine(color, Offset(x1, y1), Offset(x2, y2), STROKE, StrokeCap.Round)

@Suppress("LongParameterList")
private fun DrawScope.outline(
    color: Color,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float
) = drawRoundRect(
    color,
    Offset(left, top),
    Size(right - left, bottom - top),
    CornerRadius(radius),
    style = Stroke(STROKE, join = StrokeJoin.Round)
)

private fun DrawScope.dot(color: Color, x: Float, y: Float, radius: Float = 1.3f) =
    drawCircle(color, radius, Offset(x, y))

private fun DrawScope.polyline(color: Color, vararg points: Float) {
    val path = Path().apply {
        moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
    }
    drawPath(path, color, style = Stroke(STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun DrawScope.drawGlyph(glyph: UkGlyph, c: Color) {
    when (glyph) {
        UkGlyph.Chevron -> polyline(c, 9f, 5f, 16f, 12f, 9f, 19f)

        UkGlyph.Back -> polyline(c, 15f, 5f, 8f, 12f, 15f, 19f)

        UkGlyph.Check -> polyline(c, 5f, 12.5f, 10f, 17.5f, 19f, 7f)

        UkGlyph.Setup -> {
            drawCircle(c, 9f, Offset(12f, 12f), style = Stroke(STROKE))
            polyline(c, 8f, 12.3f, 11f, 15.3f, 16.3f, 9f)
        }

        UkGlyph.Keyboard -> {
            outline(c, 2.5f, 6.5f, 21.5f, 17.5f, 2.5f)
            listOf(7f, 10.5f, 14f, 17.5f).forEach { dot(c, it, 10.2f) }
            line(c, 8f, 14f, 16f, 14f)
        }

        UkGlyph.Suggestions -> {
            outline(c, 3f, 4.5f, 21f, 15f, 3f)
            polyline(c, 8f, 15f, 8f, 19.5f, 12.5f, 15f)
            listOf(8f, 12f, 16f).forEach { dot(c, it, 9.75f) }
        }

        UkGlyph.Gestures -> {
            val path = Path().apply {
                moveTo(3f, 17f)
                cubicTo(6f, 5f, 10f, 22f, 14f, 11f)
                cubicTo(15.5f, 7f, 17.5f, 7f, 19f, 8f)
            }
            drawPath(path, c, style = Stroke(STROKE, cap = StrokeCap.Round))
            dot(c, 19.5f, 8f, 2f)
        }

        UkGlyph.Feedback -> {
            outline(c, 8f, 3f, 16f, 21f, 2f)
            line(c, 4.5f, 8f, 4.5f, 16f)
            line(c, 19.5f, 8f, 19.5f, 16f)
            line(c, 1.8f, 10.5f, 1.8f, 13.5f)
            line(c, 22.2f, 10.5f, 22.2f, 13.5f)
        }

        UkGlyph.Palette -> {
            drawCircle(c, 9f, Offset(12f, 12f), style = Stroke(STROKE))
            dot(c, 8f, 10f)
            dot(c, 12f, 7.5f)
            dot(c, 16f, 10f)
            dot(c, 8.5f, 14.5f)
        }

        UkGlyph.Mic -> {
            outline(c, 9f, 3f, 15f, 14f, 3f)
            drawArc(
                c,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(5.5f, 6.5f),
                size = Size(13f, 11f),
                style = Stroke(STROKE, cap = StrokeCap.Round)
            )
            line(c, 12f, 17.5f, 12f, 21f)
            line(c, 8.5f, 21f, 15.5f, 21f)
        }

        UkGlyph.Clipboard -> {
            outline(c, 5f, 4.5f, 19f, 21f, 2.5f)
            outline(c, 9f, 2.5f, 15f, 6.5f, 1.5f)
            line(c, 8.5f, 11f, 15.5f, 11f)
            line(c, 8.5f, 15f, 13f, 15f)
        }

        UkGlyph.Lock -> {
            outline(c, 5f, 10.5f, 19f, 21f, 2.5f)
            drawArc(
                c,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(8.5f, 3.5f),
                size = Size(7f, 7f),
                style = Stroke(STROKE)
            )
            line(c, 8.5f, 7f, 8.5f, 10.5f)
            line(c, 15.5f, 7f, 15.5f, 10.5f)
            dot(c, 12f, 15.5f, 1.4f)
        }

        UkGlyph.Info -> {
            drawCircle(c, 9f, Offset(12f, 12f), style = Stroke(STROKE))
            dot(c, 12f, 7.8f)
            line(c, 12f, 11f, 12f, 16.5f)
        }
    }
}
