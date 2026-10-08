// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.screenshots

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.qtekfun.ultimatekeys.ime.panels.PanelKind
import com.qtekfun.ultimatekeys.ime.panels.PanelPreview
import com.qtekfun.ultimatekeys.ime.panels.PanelPreviewContent
import com.qtekfun.ultimatekeys.style.Presets
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The emoji and clipboard panels in a light and a dark preset, with different corner radii: the
 * panels must take their colours, corners and font from the active style.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PanelScreenshotTest(
    private val id: String,
    private val dark: Boolean,
    private val name: String,
    private val content: PanelPreviewContent
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun panel() {
        val style = checkNotNull(Presets.byId(id))
        compose.setContent { PanelPreview(style, dark, content) }
        val file = "panel-$name-$id-${if (dark) "dark" else "light"}.png"
        compose.onRoot().captureRoboImage(File(System.getProperty("snapshot.dir"), file).path)
    }

    companion object {
        private val variants = listOf(
            "classic" to false,
            "midnight" to true,
            "soft" to false,
            "outline" to true
        )
        private val contents = listOf(
            "emoji" to PanelPreviewContent(PanelKind.EMOJI),
            "emoji-search" to PanelPreviewContent(PanelKind.EMOJI, searching = true),
            "clipboard" to PanelPreviewContent(PanelKind.CLIPBOARD),
            "clipboard-private" to PanelPreviewContent(PanelKind.CLIPBOARD, isPrivate = true)
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{2} {0} dark={1}")
        fun parameters(): List<Array<Any>> = variants.flatMap { (id, dark) ->
            contents.map { (name, content) -> arrayOf<Any>(id, dark, name, content) }
        }
    }
}
