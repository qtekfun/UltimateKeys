// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.qtekfun.ultimatekeys.ImeStatus
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.style.Presets
import com.qtekfun.ultimatekeys.styles.EditorSection
import com.qtekfun.ultimatekeys.styles.StyleEditor
import com.qtekfun.ultimatekeys.ui.ScreenInsets
import com.qtekfun.ultimatekeys.ui.UkTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The settings screens in the light and the dark theme, on a phone with a status bar and a
 * gesture bar (the insets are supplied, so the pictures show the title clear of both). The goldens
 * are in `app/src/test/snapshots`; look at them when the design changes.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h800dp-xhdpi", application = Application::class)
class SettingsScreenshotTest(private val scene: String, private val dark: Boolean) {
    @get:Rule
    val compose = createComposeRule()

    private val insets = ScreenInsets.of(top = 28.dp, bottom = 24.dp)
    private val none: (HomeTarget) -> Unit = {}

    @Test
    fun screen() {
        compose.setContent {
            UkTheme(if (dark) TestSchemes.dark else TestSchemes.light) { Scene() }
        }
        val file = "settings-$scene-${if (dark) "dark" else "light"}.png"
        compose.onRoot().captureRoboImage(File(System.getProperty("snapshot.dir"), file).path)
    }

    @Composable
    private fun Scene() {
        val settings = KeyboardSettings()
        val ready = ImeStatus(enabled = true, selected = true)
        val update: SettingsUpdate = {}
        when (scene) {
            "home" -> HomeScreen(settings, ready, none, insets = insets)

            "typing" -> TypingScreen(settings, update, {}, insets)

            "clipboard" -> ClipboardScreen(settings, update, {}, insets)

            "gestures" -> GesturesScreen(settings, update, {}, insets)

            "editor" -> StyleEditor(Presets.default, {}, {}, insets = insets)

            "editor-keys" -> StyleEditor(
                Presets.default,
                {},
                {},
                insets = insets,
                startAt = EditorSection.Keys
            )

            else -> error("Unknown scene $scene")
        }
    }

    companion object {
        private val scenes =
            listOf("home", "typing", "clipboard", "gestures", "editor", "editor-keys")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun parameters(): List<Array<Any>> =
            scenes.flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }
}
