// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** An accent for the small icon badges of the home list; each maps to a pair of scheme roles. */
enum class BadgeAccent { Primary, Secondary, Tertiary }

/**
 * The colours of the grouped screens, all derived from one Material scheme: a tinted page, lighter
 * (or, in the dark, lifted) cards on it, and text roles that keep their contrast in both themes.
 */
@Immutable
@Suppress("LongParameterList")
class UkColors(
    val page: Color,
    val card: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tint: Color,
    val onTint: Color,
    val destructive: Color,
    val track: Color,
    val segmentTrack: Color,
    val segmentSelected: Color,
    val sliderInactive: Color,
    val badges: Map<BadgeAccent, Pair<Color, Color>>
) {
    companion object {
        /** Builds the palette from [scheme]; the dark variant is detected from the scheme itself. */
        fun from(scheme: ColorScheme): UkColors {
            val dark = scheme.surface.luminance() < DARK_LUMINANCE
            return UkColors(
                page = if (dark) scheme.surfaceContainerLowest else scheme.surfaceContainer,
                card = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest,
                separator = scheme.outlineVariant,
                label = scheme.onSurface,
                secondaryLabel = scheme.onSurfaceVariant,
                tint = scheme.primary,
                onTint = scheme.onPrimary,
                destructive = scheme.error,
                track = scheme.outline,
                segmentTrack = if (dark) {
                    scheme.surfaceContainerLowest
                } else {
                    scheme.surfaceContainerHigh
                },
                sliderInactive = scheme.outlineVariant,
                segmentSelected = if (dark) scheme.surfaceBright else scheme.surfaceContainerLowest,
                badges = mapOf(
                    BadgeAccent.Primary to (scheme.primaryContainer to scheme.onPrimaryContainer),
                    BadgeAccent.Secondary to
                        (scheme.secondaryContainer to scheme.onSecondaryContainer),
                    BadgeAccent.Tertiary to (scheme.tertiaryContainer to scheme.onTertiaryContainer)
                )
            )
        }

        private const val DARK_LUMINANCE = 0.5f
    }
}

/** The type scale of the grouped screens; sizes are in sp so large system fonts scale them. */
@Immutable
class UkTypography(
    val largeTitle: TextStyle,
    val navTitle: TextStyle,
    val body: TextStyle,
    val footnote: TextStyle,
    val sectionHeader: TextStyle
) {
    companion object {
        val Default = UkTypography(
            largeTitle = TextStyle(
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold
            ),
            navTitle = TextStyle(
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold
            ),
            body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
            footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
            sectionHeader = TextStyle(
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
        )
    }
}

private val LocalUkColors = staticCompositionLocalOf<UkColors> {
    error("UkTheme is missing: wrap the screen in UkTheme { }")
}
private val LocalUkTypography = staticCompositionLocalOf { UkTypography.Default }

/** Access to the design tokens of the current [UkTheme]. */
object UkTheme {
    val colors: UkColors
        @Composable @ReadOnlyComposable
        get() = LocalUkColors.current
    val typography: UkTypography
        @Composable @ReadOnlyComposable
        get() = LocalUkTypography.current
}

/** The Material You scheme for the system light or dark setting (the app targets Android 12+). */
@Composable
fun ukSystemColorScheme(dark: Boolean = isSystemInDarkTheme()): ColorScheme {
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

/** Applies [colorScheme] to Material and to the grouped-screen tokens derived from it. */
@Composable
fun UkTheme(colorScheme: ColorScheme = ukSystemColorScheme(), content: @Composable () -> Unit) {
    val colors = UkColors.from(colorScheme)
    CompositionLocalProvider(LocalUkColors provides colors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
