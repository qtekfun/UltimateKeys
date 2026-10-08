// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.panels

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.qtekfun.ultimatekeys.ime.surface.DynamicPalette
import com.qtekfun.ultimatekeys.ime.surface.FontProvider
import com.qtekfun.ultimatekeys.ime.surface.SurfaceStyle
import com.qtekfun.ultimatekeys.ime.surface.ToneSource
import com.qtekfun.ultimatekeys.style.ArgbColor
import com.qtekfun.ultimatekeys.style.MotionStyle
import com.qtekfun.ultimatekeys.style.Style

/**
 * What the panels draw with, resolved from the active [Style] for one light or dark variant: the
 * panel colours, corner radius, motion and the style's font.
 */
@Immutable
data class PanelTheme(
    val background: Color,
    val surface: Color,
    val text: Color,
    val hint: Color,
    val accent: Color,
    val onAccent: Color,
    val divider: Color,
    val privateTint: Color,
    val cornerDp: Float,
    val font: FontFamily,
    val fontWeight: FontWeight,
    val motion: MotionStyle,
    /** Height of the top bar, like the suggestion bar. */
    val barDp: Float,
    /** Height of the bottom bar, like a key row. */
    val rowDp: Float
) {
    companion object {
        fun resolve(
            style: Style,
            systemDark: Boolean,
            tones: ToneSource?,
            fonts: FontProvider,
            rowDp: Float
        ): PanelTheme {
            val dark = SurfaceStyle.isDark(style, systemDark)
            val p = if (tones != null && style.dynamicColors) {
                DynamicPalette.build(tones, dark)
            } else {
                style.palette(dark)
            }
            return PanelTheme(
                background = p.panelBackground.color(),
                surface = p.panelSurface.color(),
                text = p.panelText.color(),
                hint = p.hintText.color(),
                accent = p.keyAction.color(),
                onAccent = p.actionLabelText.color(),
                divider = p.barDivider.color(),
                privateTint = p.privateTint.color(),
                cornerDp = style.panels.cornerRadiusDp,
                font = FontFamily(fonts.typeface(style.labels.font, style.labels.weight)),
                fontWeight = FontWeight(style.labels.weight),
                motion = style.motion,
                barDp = style.suggestionBar.heightDp.toFloat(),
                rowDp = rowDp
            )
        }
    }
}

private fun ArgbColor.color() = Color(argb)
