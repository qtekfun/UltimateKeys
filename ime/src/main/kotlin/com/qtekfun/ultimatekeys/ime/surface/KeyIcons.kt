// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.qtekfun.ultimatekeys.ime.logic.ShiftState

/** The line icons drawn on function keys, sized by [unit] and centred on (cx, cy). */
internal object KeyIcons {
    @Suppress("LongParameterList")
    fun shift(scope: DrawScope, cx: Float, cy: Float, unit: Float, ink: Color, shift: ShiftState) {
        val p = Path().apply {
            moveTo(cx, cy - unit * 0.55f)
            lineTo(cx + unit * 0.5f, cy)
            lineTo(cx + unit * 0.2f, cy)
            lineTo(cx + unit * 0.2f, cy + unit * 0.45f)
            lineTo(cx - unit * 0.2f, cy + unit * 0.45f)
            lineTo(cx - unit * 0.2f, cy)
            lineTo(cx - unit * 0.5f, cy)
            close()
        }
        if (shift == ShiftState.OFF) {
            scope.drawPath(p, ink, style = Stroke(width = unit * STROKE))
        } else {
            scope.drawPath(p, ink)
        }
        if (shift == ShiftState.LOCKED) {
            scope.drawLine(
                ink,
                Offset(cx - unit * 0.3f, cy + unit * 0.65f),
                Offset(
                    cx + unit * 0.3f,
                    cy + unit * 0.65f
                ),
                unit * STROKE
            )
        }
    }

    fun backspace(scope: DrawScope, cx: Float, cy: Float, unit: Float, ink: Color) {
        val p = Path().apply {
            moveTo(cx - unit * 0.6f, cy)
            lineTo(cx - unit * 0.25f, cy - unit * 0.4f)
            lineTo(cx + unit * 0.6f, cy - unit * 0.4f)
            lineTo(cx + unit * 0.6f, cy + unit * 0.4f)
            lineTo(cx - unit * 0.25f, cy + unit * 0.4f)
            close()
        }
        scope.drawPath(p, ink, style = Stroke(width = unit * STROKE))
        val d = unit * 0.18f
        val x = cx + unit * 0.12f
        scope.drawLine(ink, Offset(x - d, cy - d), Offset(x + d, cy + d), unit * STROKE)
        scope.drawLine(ink, Offset(x - d, cy + d), Offset(x + d, cy - d), unit * STROKE)
    }

    fun globe(scope: DrawScope, cx: Float, cy: Float, unit: Float, ink: Color) {
        val r = unit * 0.5f
        val stroke = Stroke(width = unit * STROKE)
        scope.drawCircle(ink, r, Offset(cx, cy), style = stroke)
        scope.drawOval(ink, Offset(cx - r * 0.45f, cy - r), Size(r * 0.9f, r * 2f), style = stroke)
        scope.drawLine(ink, Offset(cx - r, cy), Offset(cx + r, cy), unit * STROKE)
    }

    fun smiley(scope: DrawScope, cx: Float, cy: Float, unit: Float, ink: Color) {
        val r = unit * 0.5f
        val stroke = Stroke(width = unit * STROKE)
        scope.drawCircle(ink, r, Offset(cx, cy), style = stroke)
        val eye = unit * 0.06f
        scope.drawCircle(ink, eye, Offset(cx - r * 0.35f, cy - r * 0.25f))
        scope.drawCircle(ink, eye, Offset(cx + r * 0.35f, cy - r * 0.25f))
        scope.drawArc(
            ink,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - r * 0.55f, cy - r * 0.45f),
            size = Size(r * 1.1f, r * 1.1f),
            style = stroke
        )
    }

    fun mic(scope: DrawScope, cx: Float, cy: Float, unit: Float, ink: Color) {
        val stroke = Stroke(width = unit * STROKE)
        val w = unit * 0.36f
        scope.drawRoundRect(
            ink,
            Offset(cx - w / 2f, cy - unit * 0.5f),
            Size(w, unit * 0.65f),
            CornerRadius(w / 2f)
        )
        scope.drawArc(
            ink,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - unit * 0.32f, cy - unit * 0.3f),
            size = Size(unit * 0.64f, unit * 0.6f),
            style = stroke
        )
        scope.drawLine(
            ink,
            Offset(cx, cy + unit * 0.3f),
            Offset(cx, cy + unit * 0.5f),
            unit * STROKE
        )
    }

    private const val STROKE = 0.1f
}
