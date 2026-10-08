// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything about how the keyboard looks (SPEC section 6). Pure data: it is serialized as the
 * `.ukstyle` file format, and [sanitized] clamps every value into its supported range so a
 * hand-edited or imported file can never produce an unusable keyboard.
 */
@Serializable
data class Style(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String = "custom",
    val name: String = "Custom",
    val description: String = "",
    val appearance: Appearance = Appearance.FOLLOW_SYSTEM,
    /** Use the wallpaper-derived system colours instead of [light] and [dark] where available. */
    val dynamicColors: Boolean = false,
    val light: Palette = Palette.DefaultLight,
    val dark: Palette = Palette.DefaultDark,
    val keys: KeyShape = KeyShape(),
    val labels: LabelStyle = LabelStyle(),
    val background: BackgroundStyle = BackgroundStyle(),
    val feedback: KeyFeedback = KeyFeedback(),
    val suggestionBar: SuggestionBarStyle = SuggestionBarStyle(),
    val bottomRow: BottomRowStyle = BottomRowStyle(),
    val panels: PanelStyle = PanelStyle(),
    val motion: MotionStyle = MotionStyle()
) {
    fun sanitized(): Style = copy(
        id = id.trim().take(MAX_ID).ifBlank { "custom" },
        name = name.trim().take(MAX_NAME).ifBlank { "Custom" },
        description = description.trim().take(MAX_DESCRIPTION),
        keys = keys.sanitized(),
        labels = labels.sanitized(),
        background = background.sanitized(),
        suggestionBar = suggestionBar.sanitized(),
        panels = panels.sanitized(),
        motion = motion.sanitized()
    )

    fun palette(dark: Boolean): Palette = if (dark) this.dark else light

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_ID = 64
        const val MAX_NAME = 40
        const val MAX_DESCRIPTION = 200
    }
}

@Serializable
enum class Appearance {
    @SerialName("system")
    FOLLOW_SYSTEM,

    @SerialName("light")
    LIGHT,

    @SerialName("dark")
    DARK
}

/** The colours of one variant (light or dark). */
@Serializable
data class Palette(
    val keyLetter: ArgbColor,
    val keyFunction: ArgbColor,
    val keySpace: ArgbColor,
    val keyAction: ArgbColor,
    val keyBorder: ArgbColor,
    val keyShadow: ArgbColor,
    val labelText: ArgbColor,
    val actionLabelText: ArgbColor,
    val hintText: ArgbColor,
    val pressHighlight: ArgbColor,
    val popupBackground: ArgbColor,
    val popupText: ArgbColor,
    val barBackground: ArgbColor,
    val barText: ArgbColor,
    val barDivider: ArgbColor,
    val panelBackground: ArgbColor,
    val panelSurface: ArgbColor,
    val panelText: ArgbColor,
    val privateTint: ArgbColor
) {
    companion object {
        val DefaultLight = Palette(
            keyLetter = ArgbColor.rgb(0xFFFFFF),
            keyFunction = ArgbColor.rgb(0xCDD1D8),
            keySpace = ArgbColor.rgb(0xFFFFFF),
            keyAction = ArgbColor.rgb(0x2F5BFF),
            keyBorder = ArgbColor.rgb(0xB9BEC8),
            keyShadow = ArgbColor(0x33000000),
            labelText = ArgbColor.rgb(0x1A1B1E),
            actionLabelText = ArgbColor.rgb(0xFFFFFF),
            hintText = ArgbColor.rgb(0x5E6470),
            pressHighlight = ArgbColor.rgb(0xB9BEC8),
            popupBackground = ArgbColor.rgb(0xFFFFFF),
            popupText = ArgbColor.rgb(0x1A1B1E),
            barBackground = ArgbColor.rgb(0xE6E8EC),
            barText = ArgbColor.rgb(0x1A1B1E),
            barDivider = ArgbColor.rgb(0xB9BEC8),
            panelBackground = ArgbColor.rgb(0xE6E8EC),
            panelSurface = ArgbColor.rgb(0xFFFFFF),
            panelText = ArgbColor.rgb(0x1A1B1E),
            privateTint = ArgbColor.rgb(0x7B3FD6)
        )
        val DefaultDark = Palette(
            keyLetter = ArgbColor.rgb(0x2B2D31),
            keyFunction = ArgbColor.rgb(0x1D1E21),
            keySpace = ArgbColor.rgb(0x2B2D31),
            keyAction = ArgbColor.rgb(0x4F7CFF),
            keyBorder = ArgbColor.rgb(0x50545C),
            keyShadow = ArgbColor(0x66000000),
            labelText = ArgbColor.rgb(0xEDEDED),
            actionLabelText = ArgbColor.rgb(0x0B0C0E),
            hintText = ArgbColor.rgb(0x9AA0AA),
            pressHighlight = ArgbColor.rgb(0x50545C),
            popupBackground = ArgbColor.rgb(0x3A3D43),
            popupText = ArgbColor.rgb(0xEDEDED),
            barBackground = ArgbColor.rgb(0x121214),
            barText = ArgbColor.rgb(0xEDEDED),
            barDivider = ArgbColor.rgb(0x3A3D43),
            panelBackground = ArgbColor.rgb(0x121214),
            panelSurface = ArgbColor.rgb(0x2B2D31),
            panelText = ArgbColor.rgb(0xEDEDED),
            privateTint = ArgbColor.rgb(0xB48CFF)
        )
    }
}

@Serializable
data class KeyShape(
    val cornerRadiusDp: Float = 8f,
    val gapXDp: Float = 4f,
    val gapYDp: Float = 6f,
    /** Height of the letter rows, as a percentage of the base row height. */
    val letterRowHeightPercent: Int = 100,
    val numberRowHeightPercent: Int = 80,
    val bottomRowHeightPercent: Int = 100,
    val border: Boolean = false,
    val borderWidthDp: Float = 1f,
    val shadow: ShadowKind = ShadowKind.NONE,
    val elevationDp: Float = 2f
) {
    fun sanitized() = copy(
        cornerRadiusDp = cornerRadiusDp.coerceIn(CORNER_RANGE),
        gapXDp = gapXDp.coerceIn(GAP_RANGE),
        gapYDp = gapYDp.coerceIn(GAP_RANGE),
        letterRowHeightPercent = letterRowHeightPercent.coerceIn(ROW_PERCENT_RANGE),
        numberRowHeightPercent = numberRowHeightPercent.coerceIn(ROW_PERCENT_RANGE),
        bottomRowHeightPercent = bottomRowHeightPercent.coerceIn(ROW_PERCENT_RANGE),
        borderWidthDp = borderWidthDp.coerceIn(BORDER_RANGE),
        elevationDp = elevationDp.coerceIn(ELEVATION_RANGE)
    )

    companion object {
        val CORNER_RANGE = 0f..28f
        val GAP_RANGE = 0f..12f
        val ROW_PERCENT_RANGE = 60..140
        val BORDER_RANGE = 0.5f..4f
        val ELEVATION_RANGE = 0f..8f
    }
}

@Serializable
enum class ShadowKind {
    @SerialName("none")
    NONE,

    @SerialName("bottom_edge")
    BOTTOM_EDGE,

    @SerialName("elevation")
    ELEVATION
}

@Serializable
data class LabelStyle(
    val font: FontChoice = FontChoice.SYSTEM,
    val weight: Int = 500,
    val sizePercent: Int = 100,
    val letterCase: LetterCase = LetterCase.FOLLOW_SHIFT,
    val showHints: Boolean = true
) {
    fun sanitized() = copy(
        weight = weight.coerceIn(WEIGHT_RANGE),
        sizePercent = sizePercent.coerceIn(SIZE_RANGE)
    )

    companion object {
        val WEIGHT_RANGE = 300..800
        val SIZE_RANGE = 70..140
    }
}

/** Bundled open-licensed fonts (SPEC section 6); `SYSTEM` uses the device default. */
@Serializable
enum class FontChoice {
    @SerialName("system")
    SYSTEM,

    @SerialName("inter")
    INTER,

    @SerialName("roboto_flex")
    ROBOTO_FLEX,

    @SerialName("atkinson_hyperlegible")
    ATKINSON_HYPERLEGIBLE,

    @SerialName("nunito")
    NUNITO
}

@Serializable
enum class LetterCase {
    @SerialName("always_upper")
    ALWAYS_UPPER,

    @SerialName("follow_shift")
    FOLLOW_SHIFT
}

@Serializable
data class BackgroundStyle(
    val kind: BackgroundKind = BackgroundKind.SOLID,
    /** Solid colour or gradient stops for the light variant (2 to 3 stops). */
    val lightColors: List<ArgbColor> = listOf(ArgbColor.rgb(0xE6E8EC)),
    val darkColors: List<ArgbColor> = listOf(ArgbColor.rgb(0x121214)),
    val gradientAngleDegrees: Int = 90,
    /** Name of an image the user picked, stored by the app; never a path or URL. */
    val imageName: String? = null,
    val imageBlur: Int = 0,
    val imageDimPercent: Int = 30
) {
    fun sanitized(): BackgroundStyle {
        val stops = if (kind == BackgroundKind.GRADIENT) GRADIENT_STOPS else SOLID_STOPS
        return copy(
            lightColors = lightColors.fitTo(stops, FALLBACK_LIGHT),
            darkColors = darkColors.fitTo(stops, FALLBACK_DARK),
            gradientAngleDegrees = ((gradientAngleDegrees % FULL_TURN) + FULL_TURN) % FULL_TURN,
            imageName = imageName?.trim()?.take(MAX_IMAGE_NAME)?.takeIf { it.isNotEmpty() },
            imageBlur = imageBlur.coerceIn(BLUR_RANGE),
            imageDimPercent = imageDimPercent.coerceIn(DIM_RANGE)
        )
    }

    private fun List<ArgbColor>.fitTo(stops: IntRange, fallback: ArgbColor): List<ArgbColor> {
        val base = ifEmpty { listOf(fallback) }
        return when {
            base.size > stops.last -> base.take(stops.last)
            base.size < stops.first -> base + List(stops.first - base.size) { base.last() }
            else -> base
        }
    }

    companion object {
        val BLUR_RANGE = 0..25
        val DIM_RANGE = 0..80
        private val SOLID_STOPS = 1..1
        private val GRADIENT_STOPS = 2..3
        private const val FULL_TURN = 360
        private const val MAX_IMAGE_NAME = 120
        private val FALLBACK_LIGHT = ArgbColor.rgb(0xE6E8EC)
        private val FALLBACK_DARK = ArgbColor.rgb(0x121214)
    }
}

@Serializable
enum class BackgroundKind {
    @SerialName("solid")
    SOLID,

    @SerialName("gradient")
    GRADIENT,

    @SerialName("image")
    IMAGE
}

@Serializable
data class KeyFeedback(
    val popup: PopupKind = PopupKind.BUBBLE,
    val pressAnimation: PressAnimation = PressAnimation.FADE
)

@Serializable
enum class PopupKind {
    @SerialName("bubble")
    BUBBLE,

    @SerialName("enlarged_key")
    ENLARGED_KEY,

    @SerialName("none")
    NONE
}

@Serializable
enum class PressAnimation {
    @SerialName("none")
    NONE,

    @SerialName("scale")
    SCALE,

    @SerialName("fade")
    FADE
}

@Serializable
data class SuggestionBarStyle(
    val layout: BarLayout = BarLayout.THREE_WITH_DIVIDERS,
    val toolIcons: ToolIcons = ToolIcons.COLLAPSED,
    val heightDp: Int = 44,
    val textSizePercent: Int = 100,
    val textWeight: Int = 500
) {
    fun sanitized() = copy(
        heightDp = heightDp.coerceIn(HEIGHT_RANGE),
        textSizePercent = textSizePercent.coerceIn(LabelStyle.SIZE_RANGE),
        textWeight = textWeight.coerceIn(LabelStyle.WEIGHT_RANGE)
    )

    companion object {
        val HEIGHT_RANGE = 32..64
    }
}

@Serializable
enum class BarLayout {
    @SerialName("three_with_dividers")
    THREE_WITH_DIVIDERS,

    @SerialName("scrolling_list")
    SCROLLING_LIST
}

@Serializable
enum class ToolIcons {
    @SerialName("shown")
    SHOWN,

    @SerialName("collapsed")
    COLLAPSED,

    @SerialName("hidden")
    HIDDEN
}

@Serializable
data class BottomRowStyle(
    val arrangement: BottomRowArrangement = BottomRowArrangement.SYMBOLS_COMMA_SPACE_MIC_ENTER,
    val micPlacement: MicPlacement = MicPlacement.BOTTOM_ROW
)

/** Which keys sit in the bottom row, left to right. */
@Serializable
enum class BottomRowArrangement {
    @SerialName("symbols_emoji_space_period_enter")
    SYMBOLS_EMOJI_SPACE_PERIOD_ENTER,

    @SerialName("symbols_comma_space_mic_enter")
    SYMBOLS_COMMA_SPACE_MIC_ENTER,

    @SerialName("symbols_globe_space_enter")
    SYMBOLS_GLOBE_SPACE_ENTER
}

@Serializable
enum class MicPlacement {
    @SerialName("bottom_row")
    BOTTOM_ROW,

    @SerialName("suggestion_bar")
    SUGGESTION_BAR
}

@Serializable
data class PanelStyle(val cornerRadiusDp: Float = 16f) {
    fun sanitized() = copy(cornerRadiusDp = cornerRadiusDp.coerceIn(CORNER_RANGE))

    companion object {
        val CORNER_RANGE = 0f..32f
    }
}

@Serializable
data class MotionStyle(
    val transition: PanelTransition = PanelTransition.SLIDE,
    val durationMs: Int = 180
) {
    fun sanitized() = copy(durationMs = durationMs.coerceIn(DURATION_RANGE))

    companion object {
        val DURATION_RANGE = 0..500
    }
}

@Serializable
enum class PanelTransition {
    @SerialName("none")
    NONE,

    @SerialName("fade")
    FADE,

    @SerialName("slide")
    SLIDE
}
