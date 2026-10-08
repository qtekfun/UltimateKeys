// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

/**
 * The built-in styles: original designs with our own names that look clearly different from each
 * other. They cannot be edited, only duplicated.
 */
object Presets {
    val Ultimate = Style(
        id = "ultimate",
        name = "Ultimate",
        description = "Rounded keys with a soft bottom edge.",
        keys = KeyShape(cornerRadiusDp = 10f, shadow = ShadowKind.BOTTOM_EDGE, elevationDp = 1.5f)
    )

    val Soft = Style(
        id = "soft",
        name = "Soft",
        description = "Pastel, very round and borderless.",
        light = palette(
            background = 0xF3EEFB, letter = 0xFFFFFF, function = 0xE4DAF5, action = 0x7A4FD8,
            actionText = 0xFFFFFF, text = 0x3A2A5A, hint = 0x6F5F8F, press = 0xD8C9F2,
            popup = 0xFFFFFF, divider = 0xD8C9F2, border = 0xD8C9F2, tint = 0x7A4FD8
        ),
        dark = palette(
            background = 0x1E1830, letter = 0x2E2548, function = 0x261F3C, action = 0xB79BFF,
            actionText = 0x1E1830, text = 0xF0E9FF, hint = 0xB5A8D6, press = 0x43376A,
            popup = 0x3A3059, divider = 0x3A3059, border = 0x43376A, tint = 0xB79BFF
        ),
        keys = KeyShape(cornerRadiusDp = 16f, gapXDp = 5f, gapYDp = 7f),
        labels = LabelStyle(font = FontChoice.NUNITO, weight = 600),
        background = solid(0xF3EEFB, 0x1E1830),
        feedback = KeyFeedback(PopupKind.BUBBLE, PressAnimation.SCALE),
        panels = PanelStyle(cornerRadiusDp = 24f)
    )

    val Flat = Style(
        id = "flat",
        name = "Flat",
        description = "High contrast, square and bold.",
        light = palette(
            background = 0xFFFFFF, letter = 0xEDEDED, function = 0xD0D0D0, action = 0xFFD400,
            actionText = 0x000000, text = 0x000000, hint = 0x3D3D3D, press = 0xBDBDBD,
            popup = 0xFFFFFF, divider = 0x808080, border = 0x000000, tint = 0x6A00FF,
            trail = 0x6A00FF
        ),
        dark = palette(
            background = 0x000000, letter = 0x1C1C1C, function = 0x2E2E2E, action = 0xFFD400,
            actionText = 0x000000, text = 0xFFFFFF, hint = 0xCFCFCF, press = 0x4A4A4A,
            popup = 0x1C1C1C, divider = 0x808080, border = 0xFFFFFF, tint = 0xC9A6FF
        ),
        keys = KeyShape(cornerRadiusDp = 2f, gapXDp = 2f, gapYDp = 2f),
        labels = LabelStyle(
            font = FontChoice.ATKINSON_HYPERLEGIBLE,
            weight = 700,
            letterCase = LetterCase.ALWAYS_UPPER,
            showHints = false
        ),
        background = solid(0xFFFFFF, 0x000000),
        feedback = KeyFeedback(PopupKind.NONE, PressAnimation.NONE),
        panels = PanelStyle(cornerRadiusDp = 0f),
        motion = MotionStyle(PanelTransition.NONE, 0)
    )

    val Outline = Style(
        id = "outline",
        name = "Outline",
        description = "Empty keys drawn with a thin line.",
        light = palette(
            background = 0xFAFAFA, letter = 0xFAFAFA, function = 0xFAFAFA, action = 0x0B6BCB,
            actionText = 0xFFFFFF, text = 0x14181F, hint = 0x4A5260, press = 0xE1E6EE,
            popup = 0xFFFFFF, divider = 0xC7CDD8, border = 0x5B6573, tint = 0x0B6BCB
        ),
        dark = palette(
            background = 0x101318, letter = 0x101318, function = 0x101318, action = 0x6CB2FF,
            actionText = 0x08121F, text = 0xEEF2F8, hint = 0xA6B0BF, press = 0x2A313D,
            popup = 0x1B212B, divider = 0x2A313D, border = 0x8591A3, tint = 0x6CB2FF
        ),
        keys = KeyShape(
            cornerRadiusDp = 12f,
            gapXDp = 6f,
            gapYDp = 8f,
            border = true,
            borderWidthDp = 1.5f
        ),
        labels = LabelStyle(font = FontChoice.INTER, weight = 500),
        background = solid(0xFAFAFA, 0x101318),
        feedback = KeyFeedback(PopupKind.ENLARGED_KEY, PressAnimation.FADE)
    )

    val Classic = Style(
        id = "classic",
        name = "Classic",
        description = "Raised keys with real depth.",
        light = palette(
            background = 0xCFD3D9, letter = 0xFBFBFC, function = 0xB4B9C1, action = 0x2F6BC4,
            actionText = 0xFFFFFF, text = 0x1D2026, hint = 0x555B66, press = 0x9EA4AE,
            popup = 0xFBFBFC, divider = 0x9EA4AE, border = 0x9EA4AE, tint = 0x2F6BC4,
            shadow = 0x66000000
        ),
        dark = palette(
            background = 0x1F2227, letter = 0x3B3F46, function = 0x2A2D33, action = 0x5B9BF0,
            actionText = 0x0B1626, text = 0xF2F4F7, hint = 0xA9AFBA, press = 0x5A5F69,
            popup = 0x4A4E56, divider = 0x4A4E56, border = 0x5A5F69, tint = 0x8DB8F5,
            shadow = 0x99000000.toInt()
        ),
        keys = KeyShape(
            cornerRadiusDp = 5f,
            gapXDp = 4f,
            gapYDp = 7f,
            shadow = ShadowKind.ELEVATION,
            elevationDp = 3f
        ),
        labels = LabelStyle(font = FontChoice.SYSTEM, weight = 500),
        background = solid(0xCFD3D9, 0x1F2227),
        feedback = KeyFeedback(PopupKind.BUBBLE, PressAnimation.FADE)
    )

    private val midnightPalette = palette(
        background = 0x0B1030, letter = 0x26305A, function = 0x1A2147, action = 0x22D3EE,
        actionText = 0x04222A, text = 0xE8ECFF, hint = 0x9FA8D8, press = 0x3B4784,
        popup = 0x313B6E, divider = 0x313B6E, border = 0x3B4784, tint = 0x22D3EE
    )

    val Midnight = Style(
        id = "midnight",
        name = "Midnight",
        description = "Deep blue gradient with a bright accent. Always dark.",
        appearance = Appearance.DARK,
        light = midnightPalette,
        dark = midnightPalette,
        keys = KeyShape(cornerRadiusDp = 10f, gapXDp = 4f, gapYDp = 6f),
        labels = LabelStyle(font = FontChoice.ROBOTO_FLEX, weight = 450),
        background = BackgroundStyle(
            kind = BackgroundKind.GRADIENT,
            lightColors = listOf(ArgbColor.rgb(0x0B1030), ArgbColor.rgb(0x2A1458)),
            darkColors = listOf(ArgbColor.rgb(0x0B1030), ArgbColor.rgb(0x2A1458)),
            gradientAngleDegrees = 100
        ),
        feedback = KeyFeedback(PopupKind.BUBBLE, PressAnimation.SCALE),
        motion = MotionStyle(PanelTransition.FADE, 220)
    )

    val Paper = Style(
        id = "paper",
        name = "Paper",
        description = "Warm cream keys with an ink-brown outline.",
        light = palette(
            background = 0xEFE6D6, letter = 0xFBF6EC, function = 0xE2D6BF, action = 0xB4532A,
            actionText = 0xFFFFFF, text = 0x2B2118, hint = 0x6B5A44, press = 0xD5C7AA,
            popup = 0xFBF6EC, divider = 0xCDBE9F, border = 0xBFAE90, tint = 0xB4532A
        ),
        dark = palette(
            background = 0x221C14, letter = 0x342B1F, function = 0x2A2318, action = 0xE08A5E,
            actionText = 0x1E140C, text = 0xF1E6D0, hint = 0xB9A98C, press = 0x4A3E2B,
            popup = 0x3E3324, divider = 0x4A3E2B, border = 0x5A4A33, tint = 0xE08A5E
        ),
        keys = KeyShape(
            cornerRadiusDp = 6f,
            gapXDp = 4f,
            gapYDp = 6f,
            border = true,
            borderWidthDp = 1f
        ),
        labels = LabelStyle(font = FontChoice.INTER, weight = 400, showHints = false),
        background = solid(0xEFE6D6, 0x221C14),
        feedback = KeyFeedback(PopupKind.ENLARGED_KEY, PressAnimation.FADE),
        suggestionBar = SuggestionBarStyle(layout = BarLayout.THREE_WITH_DIVIDERS, heightDp = 40)
    )

    val all: List<Style> = listOf(Ultimate, Soft, Flat, Outline, Classic, Midnight, Paper)

    val default: Style = Ultimate

    fun byId(id: String): Style? = all.firstOrNull { it.id == id }

    fun isPreset(id: String): Boolean = byId(id) != null

    private fun solid(light: Int, dark: Int) = BackgroundStyle(
        lightColors = listOf(ArgbColor.rgb(light)),
        darkColors = listOf(ArgbColor.rgb(dark))
    )

    /** A palette from the few colours that define a look; the panels and bar follow the background. */
    @Suppress("LongParameterList")
    private fun palette(
        background: Int,
        letter: Int,
        function: Int,
        action: Int,
        actionText: Int,
        text: Int,
        hint: Int,
        press: Int,
        popup: Int,
        divider: Int,
        border: Int,
        tint: Int,
        shadow: Int = 0x33000000,
        trail: Int = action
    ) = Palette(
        keyLetter = ArgbColor.rgb(letter),
        keyFunction = ArgbColor.rgb(function),
        keySpace = ArgbColor.rgb(letter),
        keyAction = ArgbColor.rgb(action),
        keyBorder = ArgbColor.rgb(border),
        keyShadow = ArgbColor(shadow),
        labelText = ArgbColor.rgb(text),
        actionLabelText = ArgbColor.rgb(actionText),
        hintText = ArgbColor.rgb(hint),
        pressHighlight = ArgbColor.rgb(press),
        popupBackground = ArgbColor.rgb(popup),
        popupText = ArgbColor.rgb(text),
        barBackground = ArgbColor.rgb(background),
        barText = ArgbColor.rgb(text),
        barDivider = ArgbColor.rgb(divider),
        panelBackground = ArgbColor.rgb(background),
        panelSurface = ArgbColor.rgb(letter),
        panelText = ArgbColor.rgb(text),
        privateTint = ArgbColor.rgb(tint),
        gestureTrail = ArgbColor.rgb(trail)
    )
}
