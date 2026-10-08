// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The insets an app screen respects. [bars] are the status and navigation bars and the display
 * cutout (always there when the app draws edge to edge); [ime] is the on-screen keyboard. They are
 * separate so the list can stop above the keyboard while its last row clears the gesture bar.
 * Tests build a fixed instance with [of] to see where the content lands.
 */
class ScreenInsets(val bars: WindowInsets, val ime: WindowInsets) {
    companion object {
        /** The live window insets (what a real screen uses). */
        @Composable
        fun current(): ScreenInsets = ScreenInsets(
            bars = WindowInsets.systemBars.union(WindowInsets.displayCutout),
            ime = WindowInsets.ime
        )

        /** Fixed insets, for tests and previews. */
        fun of(
            top: Dp = 0.dp,
            bottom: Dp = 0.dp,
            start: Dp = 0.dp,
            end: Dp = 0.dp,
            ime: Dp = 0.dp
        ): ScreenInsets = ScreenInsets(
            bars = WindowInsets(left = start, top = top, right = end, bottom = bottom),
            ime = WindowInsets(bottom = ime)
        )
    }
}

/**
 * Hosts [content] edge to edge: transparent system bars (the screens draw their own page colour
 * behind them and pad their content with [ScreenInsets]) inside the app theme. Every app screen
 * is set up through this, so none can forget to handle the insets.
 */
fun ComponentActivity.setUkContent(content: @Composable () -> Unit) {
    enableEdgeToEdge()
    setContent { UkTheme(content = content) }
}
