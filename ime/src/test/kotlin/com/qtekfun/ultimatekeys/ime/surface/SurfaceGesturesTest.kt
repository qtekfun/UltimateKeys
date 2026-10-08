// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import android.text.InputType
import com.qtekfun.ultimatekeys.core.FakeSettingsRepository
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.ime.KeyboardController
import com.qtekfun.ultimatekeys.ime.logic.EditorContext
import com.qtekfun.ultimatekeys.ime.logic.FakeEditorConnection
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.ime.logic.ShiftState
import com.qtekfun.ultimatekeys.layouts.ActionKey
import com.qtekfun.ultimatekeys.layouts.CharKey
import com.qtekfun.ultimatekeys.layouts.KeyAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceGesturesTest {
    private class Rig(scope: TestScope, text: String = "") {
        val editor = FakeEditorConnection(text)
        val logic = InputLogic()
        val repo =
            FakeSettingsRepository(
                KeyboardSettings(longPressDelayMs = 300, letterLayoutId = "es_qwerty")
            )
        var pickerShown = 0
        val controller =
            KeyboardController(logic, repo, scope.backgroundScope, null) { pickerShown++ }
        var last: List<PressView> = emptyList()
        val gestures = SurfaceGestures(controller, scope.backgroundScope) { last = it }
        val geometry: KeyGeometry

        init {
            logic.onStartInput(
                editor,
                EditorContext.from(InputType.TYPE_CLASS_TEXT, 0),
                restarting = false,
                initialCursor = editor.cursor
            )
            scope.runCurrent()
            geometry = KeyGeometry(
                controller.layoutFor(Page.LETTERS, controller.settings.value),
                width = 1000f,
                rowHeight = 100f,
                gapX = 4f,
                gapY = 4f
            )
            gestures.geometry = geometry
            gestures.metrics =
                GestureMetrics(
                    slop = 20f,
                    dragStep = 10f,
                    chooserMinCell = 60f,
                    chooserHeight = 80f
                )
        }

        fun key(label: String) = geometry.keys.first { (it.key as? CharKey)?.label == label }

        fun key(action: KeyAction) = geometry.keys.first {
            (it.key as? ActionKey)?.action == action
        }

        fun tap(id: Long, k: PlacedKey) {
            gestures.down(id, k.centerX, (k.top + k.bottom) / 2)
            gestures.up(id)
        }
    }

    @Test
    fun `tapping keys types text`() = runTest {
        val r = Rig(this)
        r.tap(1, r.key("h"))
        r.tap(1, r.key("o"))
        assertEquals("ho", r.editor.text.toString())
        assertEquals(2, r.controller.latency.size())
        assertTrue(r.last.isEmpty())
    }

    @Test
    fun `two fingers type independently`() = runTest {
        val r = Rig(this)
        val a = r.key("a")
        val b = r.key("b")
        r.gestures.down(1, a.centerX, a.top + 10)
        r.gestures.down(2, b.centerX, b.top + 10)
        assertEquals(2, r.last.size)
        r.gestures.up(1)
        r.gestures.up(2)
        assertEquals("ab", r.editor.text.toString())
    }

    @Test
    fun `long press opens the alternatives and sliding selects one`() = runTest {
        val r = Rig(this)
        val a = r.key("a")
        r.gestures.down(1, a.centerX, a.top + 10)
        advanceTimeBy(350)
        runCurrent()
        val chooser = r.last.single().chooser
        assertNotNull(chooser)
        assertEquals(CharKey("a").label, (a.key as CharKey).label)
        assertTrue(chooser!!.items.first() == "á")
        r.gestures.move(1, chooser.left + chooser.cellWidth * 1.5f, a.top - 10)
        assertEquals(1, r.last.single().chooser!!.selected)
        r.gestures.up(1)
        assertEquals(chooser.items[1], r.editor.text.toString())
    }

    @Test
    fun `releasing a long press without sliding types the key itself`() = runTest {
        val r = Rig(this)
        val a = r.key("a")
        r.gestures.down(1, a.centerX, a.top + 10)
        advanceTimeBy(350)
        runCurrent()
        r.gestures.up(1)
        assertEquals("a", r.editor.text.toString())
    }

    @Test
    fun `sliding to a neighbour key types the neighbour`() = runTest {
        val r = Rig(this)
        val s = r.key("s")
        val d = r.key("d")
        r.gestures.down(1, s.centerX, s.top + 10)
        r.gestures.move(1, d.centerX, d.top + 10)
        r.gestures.up(1)
        assertEquals("d", r.editor.text.toString())
    }

    @Test
    fun `delete removes on press and repeats while held`() = runTest {
        val r = Rig(this, "abcdefghij")
        val del = r.key(KeyAction.DELETE)
        r.gestures.down(1, del.centerX, del.top + 10)
        assertEquals("abcdefghi", r.editor.text.toString())
        advanceTimeBy(KeyRepeat.INITIAL_DELAY_MS + 1)
        runCurrent()
        advanceTimeBy(KeyRepeat.intervalMs(0) + KeyRepeat.intervalMs(1))
        runCurrent()
        r.gestures.up(1)
        assertTrue(r.editor.text.length < 8, r.editor.text.toString())
        val after = r.editor.text.length
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(after, r.editor.text.length)
    }

    @Test
    fun `dragging left on delete selects text and release deletes it`() = runTest {
        val r = Rig(this, "uno dos tres")
        val del = r.key(KeyAction.DELETE)
        r.gestures.down(1, del.centerX, del.top + 10)
        assertEquals("uno dos tre", r.editor.text.toString())
        r.logic.onSelectionChanged(11, 11, -1, -1)
        var x = del.centerX
        repeat(30) {
            x -= 10f
            r.gestures.move(1, x, del.top + 10)
        }
        assertTrue(r.editor.selectedText().length >= 5)
        assertEquals(PressMode.DELETE_SELECT, r.last.single().mode)
        r.gestures.up(1)
        assertTrue(r.editor.text.length < 8)
        assertEquals("", r.editor.selectedText().toString())
    }

    @Test
    fun `dragging back right shrinks the delete selection`() = runTest {
        val r = Rig(this, "uno dos tres")
        val del = r.key(KeyAction.DELETE)
        r.gestures.down(1, del.centerX, del.top + 10)
        r.logic.onSelectionChanged(11, 11, -1, -1)
        var x = del.centerX
        repeat(8) {
            x -= 10f
            r.gestures.move(1, x, del.top + 10)
        }
        val selected = r.editor.selectedText().length
        repeat(3) {
            x += 10f
            r.gestures.move(1, x, del.top + 10)
        }
        assertTrue(r.editor.selectedText().length < selected)
    }

    @Test
    fun `dragging on space moves the cursor and types no space`() = runTest {
        val r = Rig(this, "abcdef")
        val space = r.key(KeyAction.SPACE)
        r.gestures.down(1, space.centerX, space.top + 10)
        var x = space.centerX
        repeat(5) {
            x -= 25f
            r.gestures.move(1, x, space.top + 10)
        }
        r.gestures.up(1)
        assertEquals("abcdef", r.editor.text.toString())
        assertTrue(r.editor.cursor < 6)
    }

    @Test
    fun `holding space enters cursor mode without typing a space`() = runTest {
        val r = Rig(this, "abc")
        val space = r.key(KeyAction.SPACE)
        r.gestures.down(1, space.centerX, space.top + 10)
        advanceTimeBy(350)
        runCurrent()
        assertEquals(PressMode.CURSOR, r.last.single().mode)
        r.gestures.up(1)
        assertEquals("abc", r.editor.text.toString())
    }

    @Test
    fun `tapping space types a space and special keys act on release`() = runTest {
        val r = Rig(this)
        r.tap(1, r.key(KeyAction.SPACE))
        assertEquals(" ", r.editor.text.toString())
        r.tap(1, r.key(KeyAction.SHIFT))
        assertEquals(ShiftState.ONCE, r.logic.state.value.shift)
        r.tap(1, r.key(KeyAction.SWITCH_SYMBOLS))
        assertEquals(Page.SYMBOLS_1, r.logic.state.value.page)
        r.tap(1, r.key(KeyAction.ENTER))
        assertEquals(1, r.editor.enterKeys)
    }

    @Test
    fun `globe tap cycles the layout and long press shows the picker`() = runTest {
        val r = Rig(this)
        r.tap(1, r.key(KeyAction.GLOBE))
        runCurrent()
        assertEquals("en_qwerty", r.repo.settings.value.letterLayoutId)
        val globe = r.key(KeyAction.GLOBE)
        r.gestures.down(1, globe.centerX, globe.top + 10)
        advanceTimeBy(350)
        runCurrent()
        r.gestures.up(1)
        assertEquals(1, r.pickerShown)
        assertEquals("", r.editor.text.toString())
    }

    @Test
    fun `the language key menu uses smaller words than the accent chooser`() = runTest {
        val r = Rig(this)
        r.gestures.globeMenu = listOf("Settings", "Keyboards")
        val globe = r.key(KeyAction.GLOBE)
        r.gestures.down(1, globe.centerX, globe.top + 10)
        advanceTimeBy(350)
        runCurrent()
        val menu = r.last.single().chooser!!
        assertEquals(listOf("Settings", "Keyboards"), menu.items)
        assertTrue(menu.textScale < 1f)
        r.gestures.up(1)
        assertEquals(0, r.pickerShown, "lifting without choosing an entry does nothing")
        val a = r.key("a")
        r.gestures.down(2, a.centerX, a.top + 10)
        advanceTimeBy(350)
        runCurrent()
        assertEquals(1f, r.last.single().chooser!!.textScale)
        r.gestures.up(2)
    }

    @Test
    fun `cancel and unknown pointers are harmless`() = runTest {
        val r = Rig(this)
        r.gestures.move(9, 1f, 1f)
        r.gestures.up(9)
        r.gestures.cancel(9)
        val a = r.key("a")
        r.gestures.down(1, a.centerX, a.top + 5)
        r.gestures.cancel(1)
        assertTrue(r.last.isEmpty())
        r.gestures.down(2, a.centerX, a.top + 5)
        r.gestures.cancelAll()
        assertNull(r.last.firstOrNull())
        assertEquals("", r.editor.text.toString())
    }
}
