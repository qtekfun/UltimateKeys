// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.screenshots

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.qtekfun.ultimatekeys.ime.logic.EnterKind
import com.qtekfun.ultimatekeys.ime.logic.KeyboardState
import com.qtekfun.ultimatekeys.ime.logic.ShiftState
import com.qtekfun.ultimatekeys.ime.surface.KeyboardPreview
import com.qtekfun.ultimatekeys.ime.surface.PreviewContent
import com.qtekfun.ultimatekeys.ime.surface.SurfaceLabels
import com.qtekfun.ultimatekeys.languages.LanguageCatalog
import com.qtekfun.ultimatekeys.style.Presets
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The letter layouts of other languages and scripts drawn by the real renderer: the language on the space
 * bar, the accents hinted on the keys, Cyrillic and Greek labels, the Turkish dotted capitals (shift on).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class LanguageScreenshotTest(private val layoutId: String, private val shifted: Boolean) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun layout() {
        val language = checkNotNull(LanguageCatalog.languageOfLayout(layoutId))
        val labels = SurfaceLabels(
            enter = EnterKind.entries.associateWith { "Go" },
            space = language.spaceName,
            spaceIsLanguage = true,
            symbols = "?123",
            letters = "ABC",
            moreSymbols = "=\\<"
        )
        val content = PreviewContent(
            layoutId = layoutId,
            suggestions = listOf("", "", ""),
            locale = language.locale,
            state = KeyboardState(
                shift = if (shifted) ShiftState.ONCE else ShiftState.OFF,
                enterKind = EnterKind.ENTER
            )
        )
        compose.setContent {
            KeyboardPreview(Presets.default, false, content = content, labels = labels)
        }
        val name = "language-$layoutId${if (shifted) "-shift" else ""}.png"
        compose.onRoot().captureRoboImage(File(System.getProperty("snapshot.dir"), name).path)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} shift={1}")
        fun parameters(): List<Array<Any>> = listOf(
            arrayOf("fr_azerty", false),
            arrayOf("de_qwertz", false),
            arrayOf("ru_jcuken", false),
            arrayOf("tr_q", false),
            arrayOf("tr_q", true),
            arrayOf("el_greek", false),
            arrayOf("pl_qwerty", false)
        )
    }
}
