// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.screenshots

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.qtekfun.ultimatekeys.ime.surface.KeyboardPreview
import com.qtekfun.ultimatekeys.ime.surface.PreviewContent
import com.qtekfun.ultimatekeys.style.Presets
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every preset with private mode on: the tint and the filled icon must read in each look. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PrivateModeScreenshotTest(private val id: String, private val dark: Boolean) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun privatePreset() {
        val style = checkNotNull(Presets.byId(id))
        compose.setContent {
            KeyboardPreview(style, dark, content = PreviewContent(private = true))
        }
        val name = "$id-private-${if (dark) "dark" else "light"}.png"
        compose.onRoot().captureRoboImage(File(System.getProperty("snapshot.dir"), name).path)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun parameters(): List<Array<Any>> =
            Presets.all.mapIndexed { index, preset -> arrayOf<Any>(preset.id, index % 2 == 1) }
    }
}
