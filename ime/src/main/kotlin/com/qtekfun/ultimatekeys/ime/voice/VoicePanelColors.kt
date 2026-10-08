// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.voice

import androidx.compose.ui.graphics.Color
import com.qtekfun.ultimatekeys.ime.surface.DynamicPalette
import com.qtekfun.ultimatekeys.ime.surface.SurfaceStyle
import com.qtekfun.ultimatekeys.ime.surface.ToneSource
import com.qtekfun.ultimatekeys.style.ArgbColor
import com.qtekfun.ultimatekeys.style.Style

/** The colours of the voice panel, taken from the style's palette so it matches the keyboard. */
data class VoicePanelColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val hint: Color,
    val accent: Color,
    val onAccent: Color,
    val privateTint: Color
) {
    companion object {
        fun resolve(
            style: Style,
            systemDark: Boolean,
            tones: ToneSource? = null
        ): VoicePanelColors {
            val dark = SurfaceStyle.isDark(style, systemDark)
            val palette = if (tones != null && style.dynamicColors) {
                DynamicPalette.build(tones, dark)
            } else {
                style.palette(dark)
            }
            return VoicePanelColors(
                background = palette.panelBackground.color(),
                surface = palette.panelSurface.color(),
                text = palette.panelText.color(),
                hint = palette.hintText.color(),
                accent = palette.keyAction.color(),
                onAccent = palette.actionLabelText.color(),
                privateTint = palette.privateTint.color()
            )
        }

        private fun ArgbColor.color() = Color(argb)
    }
}
