// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fixed colour schemes for the screen tests. The app uses the Material You scheme of the phone;
 * tests need the same roles with stable values so the screenshots do not depend on a wallpaper.
 */
internal object TestSchemes {
    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFF3F5F90),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E3FF),
        onPrimaryContainer = Color(0xFF001B3E),
        secondaryContainer = Color(0xFFD8E3F8),
        onSecondaryContainer = Color(0xFF111C2B),
        tertiaryContainer = Color(0xFFC5EBD0),
        onTertiaryContainer = Color(0xFF002111),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF191C20),
        onSurfaceVariant = Color(0xFF44474E),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF3F3FA),
        surfaceContainer = Color(0xFFEDEDF4),
        surfaceContainerHigh = Color(0xFFE7E8EE),
        surfaceContainerHighest = Color(0xFFE2E2E9),
        surfaceBright = Color(0xFFF9F9FF),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6D0)
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFA9C7FF),
        onPrimary = Color(0xFF0A305F),
        primaryContainer = Color(0xFF254777),
        onPrimaryContainer = Color(0xFFD6E3FF),
        secondaryContainer = Color(0xFF3E4759),
        onSecondaryContainer = Color(0xFFDAE2F9),
        tertiaryContainer = Color(0xFF1F4E34),
        onTertiaryContainer = Color(0xFFC5EBD0),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE2E2E9),
        onSurfaceVariant = Color(0xFFC4C6D0),
        surfaceContainerLowest = Color(0xFF0C0E13),
        surfaceContainerLow = Color(0xFF191C20),
        surfaceContainer = Color(0xFF1D2024),
        surfaceContainerHigh = Color(0xFF282A2F),
        surfaceContainerHighest = Color(0xFF33353A),
        surfaceBright = Color(0xFF37393E),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474E)
    )
}
