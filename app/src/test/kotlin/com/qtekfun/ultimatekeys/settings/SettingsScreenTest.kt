// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.qtekfun.ultimatekeys.ImeStatus
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.ui.ScreenInsets
import com.qtekfun.ultimatekeys.ui.UkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout and behaviour of the settings screens under Robolectric: the content respects the window
 * insets (status bar, gesture bar, keyboard), and rows are single, correctly described controls.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h800dp-xhdpi", application = Application::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val ready = ImeStatus(enabled = true, selected = true)
    private val statusBar = 40.dp
    private val gestureBar = 32.dp

    private companion object {
        const val SWIPES = 6
    }

    private fun home(insets: ScreenInsets) = compose.setContent {
        UkTheme(TestSchemes.light) {
            HomeScreen(KeyboardSettings(), ready, TEST_VERSION, {}, insets = insets)
        }
    }

    @Test
    fun `the title starts below the status bar`() {
        home(ScreenInsets.of(top = statusBar, bottom = gestureBar))
        val title = compose.onNodeWithText("UltimateKeys").getUnclippedBoundsInRoot()
        // Below the status bar and the navigation bar row that sits under it.
        assertTrue("title top ${title.top} is under the status bar", title.top >= statusBar)
    }

    @Test
    fun `the first row is not hidden by the status bar and the last clears the gesture bar`() {
        home(ScreenInsets.of(top = statusBar, bottom = gestureBar))
        val first = compose.onNodeWithText("Setup").getUnclippedBoundsInRoot()
        assertTrue(first.top >= statusBar)
        // Scroll to the very end: the list must leave room for the gesture bar below the last row.
        compose.onNode(hasScrollAction()).performTouchInput { repeat(SWIPES) { swipeUp() } }
        val last = compose.onNodeWithText("About").getUnclippedBoundsInRoot()
        val rootHeight = compose.onRoot().getUnclippedBoundsInRoot().height
        assertTrue(
            "last row bottom ${last.bottom} reaches the gesture bar (root $rootHeight)",
            last.bottom <= rootHeight - gestureBar
        )
    }

    @Test
    fun `the list stops above the on-screen keyboard`() {
        val keyboard = 300.dp
        compose.setContent {
            UkTheme(TestSchemes.light) {
                SetupScreen(
                    ready,
                    {
                    },
                    {
                    },
                    {
                    },
                    ScreenInsets.of(
                        top = statusBar,
                        bottom = gestureBar,
                        ime = keyboard
                    )
                )
            }
        }
        val rootHeight = compose.onRoot().getUnclippedBoundsInRoot().height
        val list = compose.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        assertTrue(
            "list bottom ${list.bottom} is under the keyboard",
            list.bottom <= rootHeight - keyboard
        )
    }

    @Test
    fun `side cutouts push the content in`() {
        compose.setContent {
            UkTheme(TestSchemes.light) {
                HomeScreen(KeyboardSettings(), ready, TEST_VERSION, {
                }, insets = ScreenInsets.of(start = 48.dp, end = 48.dp))
            }
        }
        val row = compose.onNodeWithText("Typing").getUnclippedBoundsInRoot()
        assertTrue(row.left >= 48.dp)
    }

    @Test
    fun `a nav row is one button that opens its target`() {
        var opened: HomeTarget? = null
        compose.setContent {
            UkTheme(TestSchemes.light) {
                HomeScreen(KeyboardSettings(), ready, TEST_VERSION, {
                    opened = it
                }, insets = ScreenInsets.of())
            }
        }
        val row = compose.onNode(hasText("Typing"))
        row.assertHasClickAction()
        row.performClick()
        assertEquals(HomeTarget.Typing, opened)
    }

    @Test
    fun `a switch row is one toggleable control that reports the new value`() {
        var settings by mutableStateOf(KeyboardSettings(numberRow = false))
        compose.setContent {
            UkTheme(TestSchemes.light) {
                TypingScreen(
                    settings,
                    update = { transform -> settings = transform(settings) },
                    onBack = {},
                    insets = ScreenInsets.of(top = statusBar)
                )
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Number row"))
        val row = compose.onNode(hasText("Number row"))
        row.assertIsToggleable()
        row.assertIsOff()
        row.performClick()
        assertTrue(settings.numberRow)
        compose.waitForIdle()
        row.assertIsOn()
    }

    @Test
    fun `a slider row reads its value in its own unit`() {
        compose.setContent {
            UkTheme(TestSchemes.light) {
                TypingScreen(KeyboardSettings(heightPercent = 100), {
                }, {}, ScreenInsets.of(top = statusBar))
            }
        }
        val row = compose.onNode(hasText("Keyboard height"))
        row.assertExists()
        compose.onNode(
            SemanticsMatcher("state description 100%") {
                it.config.getOrNull(SemanticsProperties.StateDescription) == "100%"
            }
        ).assertExists()
    }

    @Test
    fun `the back button is a labelled button`() {
        compose.setContent {
            UkTheme(TestSchemes.light) {
                TypingScreen(KeyboardSettings(), {}, {}, ScreenInsets.of(top = statusBar))
            }
        }
        compose.onNode(
            SemanticsMatcher("back button") {
                it.config.getOrNull(SemanticsProperties.Role) == Role.Button &&
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Back") ==
                    true
            }
        ).assertHasClickAction()
    }

    @Test
    fun `the section titles are headings`() {
        compose.setContent {
            UkTheme(TestSchemes.light) {
                TypingScreen(KeyboardSettings(), {}, {}, ScreenInsets.of(top = statusBar))
            }
        }
        compose.onNode(
            SemanticsMatcher("typing heading") {
                SemanticsProperties.Heading in it.config &&
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text ->
                        text.text ==
                            "Typing"
                    } ==
                    true
            }
        ).assertExists()
    }
}
