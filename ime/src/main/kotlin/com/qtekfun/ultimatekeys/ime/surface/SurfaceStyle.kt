// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import com.qtekfun.ultimatekeys.style.Appearance
import com.qtekfun.ultimatekeys.style.ArgbColor
import com.qtekfun.ultimatekeys.style.BackgroundKind
import com.qtekfun.ultimatekeys.style.BarLayout
import com.qtekfun.ultimatekeys.style.FontChoice
import com.qtekfun.ultimatekeys.style.PopupKind
import com.qtekfun.ultimatekeys.style.PressAnimation
import com.qtekfun.ultimatekeys.style.ShadowKind
import com.qtekfun.ultimatekeys.style.Style
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A [Style] resolved for one variant (light or dark) into what the renderer draws with. */
data class SurfaceStyle(
    val background: Brush,
    val letterKey: Color,
    val functionKey: Color,
    val spaceKey: Color,
    val actionKey: Color,
    val keyBorder: Color,
    val keyShadow: Color,
    val text: Color,
    val actionText: Color,
    val hint: Color,
    val pressed: Color,
    val popup: Color,
    val popupText: Color,
    val barText: Color,
    val barDivider: Color,
    val cornerDp: Float,
    val gapXDp: Float,
    val gapYDp: Float,
    val borderWidthDp: Float,
    val shadow: ShadowKind,
    val elevationDp: Float,
    val font: FontChoice,
    val fontWeight: Int,
    val labelSizePercent: Int,
    val alwaysUpper: Boolean,
    val showHints: Boolean,
    val popupKind: PopupKind,
    val pressAnimation: PressAnimation,
    val barTextSizePercent: Int,
    val barTextBold: Boolean,
    val barLayout: BarLayout
) {
    val popupSelected: Color get() = actionKey
    val hasBorder: Boolean get() = borderWidthDp > 0f

    companion object {
        /** True when [style] should be drawn with its dark variant. */
        fun isDark(style: Style, systemDark: Boolean): Boolean = when (style.appearance) {
            Appearance.LIGHT -> false
            Appearance.DARK -> true
            Appearance.FOLLOW_SYSTEM -> systemDark
        }

        fun resolve(style: Style, systemDark: Boolean): SurfaceStyle {
            val dark = isDark(style, systemDark)
            val p = style.palette(dark)
            val labels = style.labels
            return SurfaceStyle(
                background = backgroundBrush(style, dark),
                letterKey = p.keyLetter.color(),
                functionKey = p.keyFunction.color(),
                spaceKey = p.keySpace.color(),
                actionKey = p.keyAction.color(),
                keyBorder = p.keyBorder.color(),
                keyShadow = p.keyShadow.color(),
                text = p.labelText.color(),
                actionText = p.actionLabelText.color(),
                hint = p.hintText.color(),
                pressed = p.pressHighlight.color(),
                popup = p.popupBackground.color(),
                popupText = p.popupText.color(),
                barText = p.barText.color(),
                barDivider = p.barDivider.color(),
                cornerDp = style.keys.cornerRadiusDp,
                gapXDp = style.keys.gapXDp,
                gapYDp = style.keys.gapYDp,
                borderWidthDp = if (style.keys.border) style.keys.borderWidthDp else 0f,
                shadow = style.keys.shadow,
                elevationDp = style.keys.elevationDp,
                font = labels.font,
                fontWeight = labels.weight,
                labelSizePercent = labels.sizePercent,
                alwaysUpper =
                    labels.letterCase == com.qtekfun.ultimatekeys.style.LetterCase.ALWAYS_UPPER,
                showHints = labels.showHints,
                popupKind = style.feedback.popup,
                pressAnimation = style.feedback.pressAnimation,
                barTextSizePercent = style.suggestionBar.textSizePercent,
                barTextBold = style.suggestionBar.textWeight >= BOLD_FROM,
                barLayout = style.suggestionBar.layout
            )
        }

        private const val BOLD_FROM = 600

        private fun backgroundBrush(style: Style, dark: Boolean): Brush {
            val bg = style.background
            val colors = (if (dark) bg.darkColors else bg.lightColors).map { it.color() }
            return when (bg.kind) {
                BackgroundKind.GRADIENT -> AngleGradient(colors, bg.gradientAngleDegrees)

                // Images are drawn by the surface itself (they need a decoded bitmap); this is what shows
                // while the image is loading or when it is missing.
                BackgroundKind.IMAGE, BackgroundKind.SOLID -> SolidColor(colors.first())
            }
        }

        /** Font loading is centralised here: bundled fonts are added in one place. */
        fun typefaceFor(font: FontChoice, weight: Int): Typeface =
            Typeface.create(FontCatalog.base(font), weight, false)
    }
}

private fun ArgbColor.color() = Color(argb)

/** Where each [FontChoice] comes from. `SYSTEM` is the device default. */
internal object FontCatalog {
    fun base(font: FontChoice): Typeface = when (font) {
        FontChoice.SYSTEM,
        FontChoice.INTER,
        FontChoice.ROBOTO_FLEX,
        FontChoice.ATKINSON_HYPERLEGIBLE,
        FontChoice.NUNITO -> Typeface.DEFAULT
    }
}

/** A linear gradient along [angleDegrees] (0 = left to right, 90 = top to bottom) over the whole area. */
class AngleGradient(private val colors: List<Color>, private val angleDegrees: Int) :
    ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val dx = cos(radians).toFloat()
        val dy = sin(radians).toFloat()
        // Half the extent of the box projected on the gradient direction.
        val half = (abs(dx) * size.width + abs(dy) * size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        return LinearGradientShader(
            from = Offset(center.x - dx * half, center.y - dy * half),
            to = Offset(center.x + dx * half, center.y + dy * half),
            colors = colors
        )
    }
}
