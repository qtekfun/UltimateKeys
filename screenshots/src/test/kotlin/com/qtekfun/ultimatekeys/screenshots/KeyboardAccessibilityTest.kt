// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import com.qtekfun.ultimatekeys.ime.surface.A11yAction
import com.qtekfun.ultimatekeys.ime.surface.A11yNode
import com.qtekfun.ultimatekeys.ime.surface.A11yTarget
import com.qtekfun.ultimatekeys.ime.surface.KeyboardAccessibilityLayer
import com.qtekfun.ultimatekeys.layouts.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The virtual key nodes a screen reader explores: names, roles, states, clicks and extra actions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class KeyboardAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val activated = mutableListOf<A11yTarget>()

    private val nodes = listOf(
        A11yNode(
            "a",
            0f,
            0f,
            100f,
            100f,
            "a",
            A11yTarget.Type("a"),
            actions = listOf(
                A11yAction("type á", A11yTarget.Type("á"))
            )
        ),
        A11yNode(
            "shift",
            100f,
            0f,
            200f,
            100f,
            "shift",
            A11yTarget.Action(KeyAction.SHIFT),
            state = "caps lock"
        ),
        A11yNode("private", 200f, 0f, 300f, 100f, "private", A11yTarget.TogglePrivate, state = "on")
    )

    private fun show() = compose.setContent {
        KeyboardAccessibilityLayer(nodes, { activated += it })
    }

    @Test
    fun `each node is a named button that a click activates`() {
        show()
        nodes.forEach { node ->
            compose.onNodeWithContentDescription(node.description)
                .assertHasClickAction()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.Role,
                        androidx.compose.ui.semantics.Role.Button
                    )
                )
        }
        compose.onNodeWithContentDescription("a").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription(
            "shift"
        ).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(A11yTarget.Type("a"), A11yTarget.Action(KeyAction.SHIFT)), activated)
    }

    @Test
    fun `state is exposed and does not rely on colour`() {
        show()
        compose.onNodeWithContentDescription("shift")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "caps lock"))
        compose.onNodeWithContentDescription("private")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "on"))
    }

    @Test
    fun `alternatives are custom actions that type the alternative`() {
        show()
        val config = compose.onNodeWithContentDescription("a").fetchSemanticsNode().config
        val actions = config[SemanticsActions.CustomActions]
        assertEquals(listOf("type á"), actions.map { it.label })
        assertTrue(actions.single().action())
        assertEquals(listOf<A11yTarget>(A11yTarget.Type("á")), activated)
    }

    @Test
    fun `nothing else is announced`() {
        show()
        compose.onAllNodesWithContentDescription("a").assertCountEquals(1)
        assertTrue(activated.isEmpty())
    }
}
