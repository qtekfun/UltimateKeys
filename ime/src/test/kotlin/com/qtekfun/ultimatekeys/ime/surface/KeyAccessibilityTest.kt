// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import android.text.InputType
import com.qtekfun.ultimatekeys.core.FakeSettingsRepository
import com.qtekfun.ultimatekeys.ime.KeyboardController
import com.qtekfun.ultimatekeys.ime.logic.EditorContext
import com.qtekfun.ultimatekeys.ime.logic.EnterKind
import com.qtekfun.ultimatekeys.ime.logic.FakeEditorConnection
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.logic.KeyboardState
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.ime.logic.ShiftState
import com.qtekfun.ultimatekeys.ime.suggest.SuggestionState
import com.qtekfun.ultimatekeys.layouts.ActionKey
import com.qtekfun.ultimatekeys.layouts.BottomRow
import com.qtekfun.ultimatekeys.layouts.BottomSlot
import com.qtekfun.ultimatekeys.layouts.CharKey
import com.qtekfun.ultimatekeys.layouts.KeyAction
import com.qtekfun.ultimatekeys.layouts.LayoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyAccessibilityTest {
    private companion object {
        const val EPS = 0.5f
    }

    private val labels = A11yLabels(
        enter = EnterKind.entries.associateWith { "enter-${it.name}" },
        space = "space",
        shift = "shift",
        shiftOnce = "once",
        shiftLocked = "locked",
        shiftOff = "off",
        delete = "delete",
        globe = "language",
        keyboardPicker = "picker",
        symbols = "symbols",
        letters = "letters",
        moreSymbols = "more",
        emoji = "emoji",
        dictate = "dictate",
        clipboard = "clipboard",
        privateMode = "private",
        privateOn = "on",
        privateOff = "off",
        typeAlternative = { "type $it" },
        suggestion = { "suggest $it" }
    )

    private val slots = listOf(
        BottomSlot.SWITCH,
        BottomSlot.GLOBE,
        BottomSlot.COMMA,
        BottomSlot.SPACE,
        BottomSlot.PERIOD,
        BottomSlot.EMOJI,
        BottomSlot.MIC,
        BottomSlot.ENTER
    )
    private val layout = BottomRow.apply(LayoutRepository.pages("es_qwerty", false).letters, slots)
    private val geometry = KeyGeometry(layout, width = 1000f, rowHeight = 100f, top = 80f)
    private val noStrip = A11yStrip(SuggestionState(), false, false, false, false, false)

    private fun keyNodes(state: KeyboardState = KeyboardState()) =
        KeyAccessibility.keyNodes(geometry, state, labels)

    private fun node(nodes: List<A11yNode>, description: String) =
        nodes.first { it.description == description }

    @Test
    fun `every key becomes one node with a description inside the surface`() {
        val nodes = keyNodes()
        assertEquals(geometry.keys.size, nodes.size)
        assertTrue(nodes.all { it.description.isNotBlank() })
        assertEquals(nodes.size, nodes.map { it.id }.toSet().size)
        assertTrue(nodes.all { it.left >= -EPS && it.right <= 1000f + EPS && it.top >= 80f - EPS })
    }

    @Test
    fun `letters follow the shift state and symbols are left alone`() {
        assertNotNull(keyNodes().firstOrNull { it.description == "q" })
        assertNotNull(
            keyNodes(KeyboardState(shift = ShiftState.ONCE)).firstOrNull {
                it.description ==
                    "Q"
            }
        )
        assertNotNull(
            keyNodes(KeyboardState(shift = ShiftState.LOCKED)).firstOrNull {
                it.description ==
                    "Q"
            }
        )
        val symbols = KeyboardState(shift = ShiftState.ONCE, page = Page.SYMBOLS_1)
        assertNull(keyNodes(symbols).firstOrNull { it.description == "Q" })
    }

    @Test
    fun `the typed text is the key output and not the spoken label`() {
        val q = node(keyNodes(KeyboardState(shift = ShiftState.ONCE)), "Q")
        assertEquals(A11yTarget.Type("q"), q.target)
    }

    @Test
    fun `shift announces its state`() {
        fun shiftState(shift: ShiftState) = keyNodes(KeyboardState(shift = shift)).first {
            it.target ==
                A11yTarget.Action(KeyAction.SHIFT)
        }
        assertEquals("off", shiftState(ShiftState.OFF).state)
        assertEquals("once", shiftState(ShiftState.ONCE).state)
        assertEquals("locked", shiftState(ShiftState.LOCKED).state)
        assertEquals("shift", shiftState(ShiftState.OFF).description)
    }

    @Test
    fun `enter takes the label of the editor action`() {
        EnterKind.entries.forEach { kind ->
            val enter = keyNodes(KeyboardState(enterKind = kind))
                .first { it.target == A11yTarget.Action(KeyAction.ENTER) }
            assertEquals("enter-${kind.name}", enter.description)
        }
    }

    @Test
    fun `long press alternatives are reachable as actions`() {
        val withAlternatives = geometry.keys.map { it.key }.filterIsInstance<CharKey>()
            .first { it.alternatives.isNotEmpty() }
        val node = keyNodes().first { it.target == A11yTarget.Type(withAlternatives.output) }
        assertEquals(
            withAlternatives.alternatives.map { A11yAction("type $it", A11yTarget.Type(it)) },
            node.actions
        )
    }

    @Test
    fun `the function keys are described`() {
        val nodes = keyNodes()
        val targets = geometry.keys.map {
            it.key
        }.filterIsInstance<ActionKey>().map { it.action }.toSet()
        assertTrue(
            targets.containsAll(
                listOf(KeyAction.DELETE, KeyAction.SPACE, KeyAction.GLOBE, KeyAction.MIC)
            )
        )
        assertEquals("delete", node(nodes, "delete").description)
        assertEquals("space", node(nodes, "space").description)
        assertEquals("dictate", node(nodes, "dictate").description)
        assertEquals("emoji", node(nodes, "emoji").description)
        assertEquals("symbols", node(nodes, "symbols").description)
        val globe = node(nodes, "language")
        assertEquals(listOf(A11yAction("picker", A11yTarget.KeyboardPicker)), globe.actions)
    }

    @Test
    fun `the symbol pages name their switch keys`() {
        val pageLabels = listOf(
            KeyAction.SWITCH_LETTERS to "letters",
            KeyAction.SWITCH_SYMBOLS to "symbols",
            KeyAction.SWITCH_SYMBOLS_2 to "more"
        )
        val all = listOf("symbols_1", "symbols_2").flatMap {
            val g = KeyGeometry(LayoutRepository.load(it), 1000f, 100f)
            KeyAccessibility.keyNodes(g, KeyboardState(page = Page.SYMBOLS_1), labels)
        }
        pageLabels.forEach { (action, text) ->
            all.filter {
                it.target == A11yTarget.Action(action)
            }.forEach { assertEquals(text, it.description) }
        }
        assertTrue(all.any { it.target == A11yTarget.Action(KeyAction.SWITCH_LETTERS) })
    }

    @Test
    fun `no strip nodes without a strip`() {
        val flat = KeyGeometry(layout, 1000f, 100f, top = 0f)
        assertTrue(KeyAccessibility.stripNodes(flat, noStrip.copy(toggle = true), labels).isEmpty())
    }

    @Test
    fun `strip buttons and suggestions come in reading order with their state`() {
        val strip = A11yStrip(
            suggestions = SuggestionState(listOf("hol", "hola", "")),
            toggle = true,
            privateOn = true,
            canTogglePrivate = true,
            mic = true,
            tools = true
        )
        val nodes = KeyAccessibility.stripNodes(geometry, strip, labels)
        assertEquals(
            listOf("private", "clipboard", "emoji", "suggest hol", "suggest hola", "dictate"),
            nodes.map { it.description }
        )
        assertEquals("on", nodes.first().state)
        assertEquals(nodes.sortedBy { it.left }, nodes)
        assertTrue(nodes.all { it.top == 0f && it.bottom == 80f })
        // Four buttons of the strip height, three suggestion cells share the rest.
        assertEquals(80f, nodes[0].width)
        assertEquals(A11yTarget.Suggestion(1), nodes[4].target)
        assertEquals(1000f, nodes.last().right)
        assertEquals(A11yTarget.OpenClipboard, nodes[1].target)
        assertEquals(A11yTarget.Action(KeyAction.EMOJI), nodes[2].target)
    }

    @Test
    fun `private mode reads off when it is off`() {
        val nodes = KeyAccessibility.stripNodes(geometry, noStrip.copy(toggle = true), labels)
        assertEquals("off", nodes.single().state)
        assertEquals(A11yTarget.TogglePrivate, nodes.single().target)
    }

    @Test
    fun `nodes lists the strip before the keys`() {
        val strip = noStrip.copy(suggestions = SuggestionState(listOf("", "hola", "")))
        val nodes = KeyAccessibility.nodes(geometry, KeyboardState(), strip, labels)
        assertEquals("suggest hola", nodes.first().description)
        assertEquals(1 + geometry.keys.size, nodes.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityTargetsTest {
    private class Rig(scope: kotlinx.coroutines.CoroutineScope) {
        val logic = InputLogic()
        val editor = FakeEditorConnection()
        var picker = 0
        val controller =
            KeyboardController(logic, FakeSettingsRepository(), scope, null) { picker++ }

        init {
            logic.onStartInput(editor, EditorContext.from(InputType.TYPE_CLASS_TEXT, 0), false, 0)
        }
    }

    @Test
    fun `typing and function targets reach the input logic`() = runTest {
        val rig = Rig(backgroundScope)
        runCurrent()
        AccessibilityTargets.activate(rig.controller, A11yTarget.Type("h"))
        AccessibilityTargets.activate(rig.controller, A11yTarget.Type("i"))
        assertEquals("hi", rig.editor.text.toString())
        AccessibilityTargets.activate(rig.controller, A11yTarget.Action(KeyAction.DELETE))
        assertEquals("h", rig.editor.text.toString())
        AccessibilityTargets.activate(rig.controller, A11yTarget.Action(KeyAction.SPACE))
        assertEquals("h ", rig.editor.text.toString())
    }

    @Test
    fun `shift, private mode and the picker`() = runTest {
        val rig = Rig(backgroundScope)
        runCurrent()
        AccessibilityTargets.activate(rig.controller, A11yTarget.Action(KeyAction.SHIFT))
        assertEquals(ShiftState.ONCE, rig.logic.state.value.shift)
        AccessibilityTargets.activate(rig.controller, A11yTarget.TogglePrivate)
        assertTrue(rig.controller.privacy.isPrivate)
        AccessibilityTargets.activate(rig.controller, A11yTarget.KeyboardPicker)
        assertEquals(1, rig.picker)
        AccessibilityTargets.activate(rig.controller, A11yTarget.OpenClipboard)
        assertEquals(
            com.qtekfun.ultimatekeys.ime.panels.PanelKind.CLIPBOARD,
            rig.controller.panels.kind.value
        )
    }

    @Test
    fun `a suggestion target taps the strip slot`() = runTest {
        val rig = Rig(backgroundScope)
        runCurrent()
        // Nothing suggested yet: the tap is ignored rather than failing.
        AccessibilityTargets.activate(rig.controller, A11yTarget.Suggestion(1))
        assertEquals("", rig.editor.text.toString())
    }
}
