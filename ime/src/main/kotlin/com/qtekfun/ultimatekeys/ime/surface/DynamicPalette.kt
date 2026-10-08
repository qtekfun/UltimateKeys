// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import android.content.Context
import com.qtekfun.ultimatekeys.style.ArgbColor
import com.qtekfun.ultimatekeys.style.Palette

/** The system's wallpaper-derived tonal colours, looked up by tone (0 to 1000). */
class ToneSource(
    val accent: (Int) -> Int,
    val neutral: (Int) -> Int,
    val neutralVariant: (Int) -> Int
) {
    companion object {
        /** Reads the Material You colour resources the system provides (Android 12 and later). */
        fun fromContext(context: Context): ToneSource {
            fun resolve(id: Int) = context.getColor(id)
            return ToneSource(
                accent = { resolve(accentId(it)) },
                neutral = { resolve(neutralId(it)) },
                neutralVariant = { resolve(neutralVariantId(it)) }
            )
        }

        @Suppress("CyclomaticComplexMethod")
        private fun accentId(tone: Int) = when (tone) {
            T0 -> android.R.color.system_accent1_0
            T100 -> android.R.color.system_accent1_100
            T200 -> android.R.color.system_accent1_200
            T600 -> android.R.color.system_accent1_600
            T900 -> android.R.color.system_accent1_900
            else -> error("Unsupported tone $tone")
        }

        @Suppress("CyclomaticComplexMethod")
        private fun neutralId(tone: Int) = when (tone) {
            T0 -> android.R.color.system_neutral1_0
            T100 -> android.R.color.system_neutral1_100
            T200 -> android.R.color.system_neutral1_200
            T600 -> android.R.color.system_neutral1_600
            T700 -> android.R.color.system_neutral1_700
            T800 -> android.R.color.system_neutral1_800
            T900 -> android.R.color.system_neutral1_900
            else -> error("Unsupported tone $tone")
        }

        private fun neutralVariantId(tone: Int) = when (tone) {
            T100 -> android.R.color.system_neutral2_100
            T200 -> android.R.color.system_neutral2_200
            T300 -> android.R.color.system_neutral2_300
            T600 -> android.R.color.system_neutral2_600
            T700 -> android.R.color.system_neutral2_700
            T800 -> android.R.color.system_neutral2_800
            T900 -> android.R.color.system_neutral2_900
            else -> error("Unsupported tone $tone")
        }
    }
}

const val T0 = 0
const val T100 = 100
const val T200 = 200
const val T300 = 300
const val T600 = 600
const val T700 = 700
const val T800 = 800
const val T900 = 900

/** Builds a [Palette] out of the system's tonal colours (SPEC section 6, Material You). */
object DynamicPalette {
    fun build(tones: ToneSource, dark: Boolean): Palette {
        fun a(tone: Int) = ArgbColor(tones.accent(tone))
        fun n(tone: Int) = ArgbColor(tones.neutral(tone))
        fun v(tone: Int) = ArgbColor(tones.neutralVariant(tone))
        return if (dark) {
            Palette(
                keyLetter = v(T800),
                keyFunction = v(T900),
                keySpace = v(T800),
                keyAction = a(T200),
                keyBorder = v(T700),
                keyShadow = ArgbColor(SHADOW_DARK),
                labelText = n(T100),
                actionLabelText = a(T900),
                hintText = v(T200),
                pressHighlight = v(T700),
                popupBackground = v(T700),
                popupText = n(T100),
                barBackground = n(T900),
                barText = n(T100),
                barDivider = v(T700),
                panelBackground = n(T900),
                panelSurface = v(T800),
                panelText = n(T100),
                privateTint = a(T200)
            )
        } else {
            Palette(
                keyLetter = n(T0),
                keyFunction = v(T200),
                keySpace = n(T0),
                keyAction = a(T600),
                keyBorder = v(T300),
                keyShadow = ArgbColor(SHADOW_LIGHT),
                labelText = n(T900),
                actionLabelText = a(T0),
                hintText = v(T600),
                pressHighlight = v(T300),
                popupBackground = n(T0),
                popupText = n(T900),
                barBackground = v(T100),
                barText = n(T900),
                barDivider = v(T300),
                panelBackground = v(T100),
                panelSurface = n(T0),
                panelText = n(T900),
                privateTint = a(T600)
            )
        }
    }

    private const val SHADOW_LIGHT = 0x33000000
    private const val SHADOW_DARK = 0x66000000
}
