// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.qtekfun.ultimatekeys.ime.BottomRowFeatures
import com.qtekfun.ultimatekeys.ime.surface.KeyboardPreview
import com.qtekfun.ultimatekeys.ime.surface.PreviewContent
import com.qtekfun.ultimatekeys.ime.voice.PanelAction
import com.qtekfun.ultimatekeys.ime.voice.VoicePanelColors
import com.qtekfun.ultimatekeys.ime.voice.VoicePanelContent
import com.qtekfun.ultimatekeys.style.BottomRowArrangement
import com.qtekfun.ultimatekeys.style.MicPlacement
import com.qtekfun.ultimatekeys.style.Presets
import com.qtekfun.ultimatekeys.voice.DictationError
import com.qtekfun.ultimatekeys.voice.DictationState
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every state of the dictation panel, in two looks, so a change of how it reads is a visible diff. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class VoicePanelScreenshotTest(
    private val name: String,
    private val id: String,
    private val dark: Boolean,
    private val state: DictationState,
    private val action: PanelAction?,
    private val level: Float,
    private val private: Boolean
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun panel() {
        val style = checkNotNull(Presets.byId(id))
        val colors = VoicePanelColors.resolve(style, dark)
        compose.setContent {
            Box(Modifier.fillMaxWidth().height(PANEL_HEIGHT_DP.dp)) {
                VoicePanelContent(
                    state = state,
                    action = action,
                    isPrivate = private,
                    colors = colors,
                    cornerDp = style.panels.cornerRadiusDp,
                    level = level,
                    phase = 0.25f,
                    onStop = {},
                    onCancel = {},
                    onAction = {}
                )
            }
        }
        val file = "voice-$name-$id-${if (dark) "dark" else "light"}.png"
        compose.onRoot().captureRoboImage(File(System.getProperty("snapshot.dir"), file).path)
    }

    companion object {
        private const val PANEL_HEIGHT_DP = 270

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1} dark={2}")
        fun parameters(): List<Array<Any?>> {
            val states = listOf(
                Case("listening", DictationState.Listening(0.15f, false), null, 0.15f, false),
                Case("speaking", DictationState.Listening(0.8f, true), null, 0.8f, false),
                Case("listening-private", DictationState.Listening(0.4f, true), null, 0.4f, true),
                Case("transcribing", DictationState.Transcribing, null, 0f, false),
                Case(
                    "no-permission",
                    DictationState.Failed(DictationError.NO_PERMISSION),
                    PanelAction.ALLOW_MICROPHONE,
                    0f,
                    false
                ),
                Case(
                    "no-model",
                    DictationState.Failed(DictationError.NO_MODEL),
                    PanelAction.OPEN_MODELS,
                    0f,
                    false
                ),
                Case(
                    "no-speech",
                    DictationState.Failed(DictationError.NO_SPEECH),
                    PanelAction.TRY_AGAIN,
                    0f,
                    false
                )
            )
            return states.flatMap { c ->
                listOf("ultimate" to false, "midnight" to true).map { (id, dark) ->
                    arrayOf<Any?>(c.name, id, dark, c.state, c.action, c.level, c.private)
                }
            }
        }
    }

    private class Case(
        val name: String,
        val state: DictationState,
        val action: PanelAction?,
        val level: Float,
        val private: Boolean
    )
}

/** The microphone placed in the suggestion bar by the style. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SuggestionBarMicScreenshotTest(private val placement: MicPlacement) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun placement() {
        val base = checkNotNull(Presets.byId("ultimate"))
        val style = base.copy(
            bottomRow = base.bottomRow.copy(
                arrangement = BottomRowArrangement.SYMBOLS_GLOBE_COMMA_SPACE_PERIOD_ENTER,
                micPlacement = placement
            )
        )
        compose.setContent {
            KeyboardPreview(
                style,
                dark = false,
                content = PreviewContent(features = BottomRowFeatures(voice = true))
            )
        }
        val file = "voice-mic-${placement.name.lowercase()}-ultimate-light.png"
        compose.onRoot().captureRoboImage(File(System.getProperty("snapshot.dir"), file).path)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters(): List<Array<Any>> = MicPlacement.entries.map { arrayOf<Any>(it) }
    }
}
