// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import android.text.InputType
import com.qtekfun.ultimatekeys.core.FakeSettingsRepository
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.engine.Suggestion
import com.qtekfun.ultimatekeys.gesture.GestureVocabulary
import com.qtekfun.ultimatekeys.ime.KeyboardController
import com.qtekfun.ultimatekeys.ime.logic.EditorContext
import com.qtekfun.ultimatekeys.ime.logic.FakeEditorConnection
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.ime.suggest.FakeEngine
import com.qtekfun.ultimatekeys.layouts.ActionKey
import com.qtekfun.ultimatekeys.layouts.CharKey
import com.qtekfun.ultimatekeys.layouts.KeyAction
import kotlin.math.hypot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Gliding over the letters: from the touches to the text in the editor. */
@OptIn(ExperimentalCoroutinesApi::class)
class GestureTypingTest {
    private class Rig(
        private val scope: TestScope,
        settings: KeyboardSettings = KeyboardSettings(letterLayoutId = "es_qwerty"),
        installVocabulary: Boolean = true
    ) {
        val editor = FakeEditorConnection()
        val logic = InputLogic()
        val engine = FakeEngine(next = { listOf(Suggestion("mundo", 10)) })
        val repo = FakeSettingsRepository(settings)
        val controller = KeyboardController(
            logic,
            repo,
            scope.backgroundScope,
            null,
            engine,
            StandardTestDispatcher(scope.testScheduler)
        ) {}
        var last: List<PressView> = emptyList()
        val gestures = SurfaceGestures(controller, scope.backgroundScope) { last = it }
        val geometry: KeyGeometry

        init {
            logic.onStartInput(
                editor,
                EditorContext.from(InputType.TYPE_CLASS_TEXT, 0),
                restarting = false,
                initialCursor = 0
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
            gestures.metrics = GestureMetrics(
                slop = 20f,
                dragStep = 10f,
                chooserMinCell = 60f,
                chooserHeight = 80f
            )
            if (installVocabulary) {
                controller.gesture.install(
                    GestureVocabulary.Builder(listOf("es", "en"))
                        .add(0, "hola", 200)
                        .add(0, "hora", 150)
                        .add(0, "mundo", 190)
                        .add(0, "mundial", 120)
                        .add(1, "hello", 180)
                        .build()
                )
            }
        }

        fun key(label: String) = geometry.keys.first { (it.key as? CharKey)?.label == label }

        fun center(label: String) = key(label).let { it.centerX to (it.top + it.bottom) / 2 }

        /** Drags a finger through the centres of [word]'s letters in steps of 10 px, then lifts it. */
        fun glide(word: String, id: Long = 1L, lift: Boolean = true) {
            val points = word.map { center(it.toString()) }
            gestures.down(id, points.first().first, points.first().second)
            points.zipWithNext().forEach { (a, b) ->
                val steps = (
                    hypot(
                        b.first - a.first,
                        b.second - a.second
                    ) / STEP
                    ).toInt().coerceAtLeast(1)
                for (i in 1..steps) {
                    gestures.move(
                        id,
                        a.first + (b.first - a.first) * i / steps,
                        a.second + (b.second - a.second) * i / steps
                    )
                }
            }
            if (lift) gestures.up(id)
            scope.runCurrent()
        }

        companion object {
            const val STEP = 10f
        }
    }

    @Test
    fun `gliding over the letters types the word and a space`() = runTest {
        val rig = Rig(this)
        rig.glide("hola")
        assertEquals("hola ", rig.editor.text.toString())
    }

    @Test
    fun `the strip offers the alternatives and a tap swaps the word`() = runTest {
        val rig = Rig(this)
        rig.glide("hola")
        val strip = rig.controller.suggestions.state.value
        assertTrue(strip.gesture)
        assertEquals("hola", strip.slots[1])
        assertTrue(strip.slots.filter { it.isNotEmpty() }.size >= 2)
        val other = strip.slots[0].ifEmpty { strip.slots[2] }
        rig.controller.onSuggestionTapped(if (strip.slots[0].isNotEmpty()) 0 else 2)
        assertEquals("$other ", rig.editor.text.toString())
        val swapped = rig.controller.suggestions.state.value
        assertEquals(other, swapped.slots[1])
        assertTrue("hola" in swapped.slots)
        // The word in the centre is already typed: tapping it changes nothing.
        rig.controller.onSuggestionTapped(1)
        assertEquals("$other ", rig.editor.text.toString())
        rig.logic.onDelete()
        assertEquals("", rig.editor.text.toString())
    }

    @Test
    fun `typing after a gesture returns the strip to ordinary suggestions`() = runTest {
        val rig = Rig(this)
        rig.glide("hola")
        rig.logic.onText("x")
        runCurrent()
        assertFalse(rig.controller.suggestions.state.value.gesture)
    }

    @Test
    fun `backspace takes the glided word back`() = runTest {
        val rig = Rig(this)
        rig.glide("hola")
        rig.glide("mundo", id = 2L)
        assertEquals("hola mundo ", rig.editor.text.toString())
        rig.logic.onDelete()
        assertEquals("hola ", rig.editor.text.toString())
    }

    @Test
    fun `an English word is glided as easily as a Spanish one`() = runTest {
        val rig = Rig(this)
        rig.glide("hello")
        assertEquals("hello ", rig.editor.text.toString())
    }

    @Test
    fun `a long word the engine did not predict still wins when glided exactly`() = runTest {
        val rig = Rig(this)
        rig.glide("mundial")
        assertTrue(rig.editor.text.toString() in listOf("mundial ", "mundo "))
    }

    @Test
    fun `gesture words are learned unless typing is private`() = runTest {
        val open = Rig(this)
        open.glide("hola")
        open.logic.onText("x")
        runCurrent()
        assertEquals(listOf("hola"), open.engine.learned.map { it.first })

        val private = Rig(this)
        private.controller.privacy.toggleManual()
        assertTrue(private.controller.privacy.isPrivate)
        private.glide("hola")
        private.logic.onText("x")
        private.glide("mundo", id = 2L)
        private.logic.onFinishInput()
        runCurrent()
        assertTrue(private.engine.learned.isEmpty())
        assertEquals("holax mundo ", private.editor.text.toString().replace("hola x", "holax"))
    }

    @Test
    fun `the person's own words can be glided`() = runTest {
        val rig = Rig(this)
        rig.engine.userWords += "hobo"
        rig.glide("hobo")
        assertEquals("hobo ", rig.editor.text.toString())
    }

    @Test
    fun `with the setting off a swipe is the old slide onto a key`() = runTest {
        val rig = Rig(this, KeyboardSettings(letterLayoutId = "es_qwerty", gestureTyping = false))
        rig.glide("hola")
        assertEquals("a", rig.editor.text.toString())
    }

    @Test
    fun `nothing happens before the word lists are loaded`() = runTest {
        val rig = Rig(this, installVocabulary = false)
        assertFalse(rig.controller.gesture.ready)
        rig.glide("hola")
        assertEquals("a", rig.editor.text.toString())
    }

    @Test
    fun `a glide that decodes to nothing types nothing`() = runTest {
        val rig = Rig(this)
        rig.glide("zxcvbnm")
        assertEquals("", rig.editor.text.toString())
    }

    @Test
    fun `a short slide onto the next key is still a key press`() = runTest {
        val rig =
            Rig(this, KeyboardSettings(letterLayoutId = "es_qwerty", gestureSensitivity = 100))
        val g = rig.key("g")
        val h = rig.key("h")
        rig.gestures.down(1, g.centerX, (g.top + g.bottom) / 2)
        // Far enough to count as a gesture at full sensitivity, too short to be a word.
        rig.gestures.move(1, g.right, (g.top + g.bottom) / 2)
        rig.gestures.move(1, h.centerX - 5f, (h.top + h.bottom) / 2)
        assertEquals(PressMode.GESTURE, rig.last.single().mode)
        rig.gestures.up(1)
        runCurrent()
        assertEquals("h", rig.editor.text.toString())
        assertTrue(rig.last.isEmpty())
    }

    @Test
    fun `the trail is published while gliding and only when it is switched on`() = runTest {
        val rig = Rig(this)
        rig.glide("hola", lift = false)
        val view = rig.last.single()
        assertEquals(PressMode.GESTURE, view.mode)
        assertTrue(view.trail.size >= 4 && view.trail.size <= 2 * 48)
        rig.gestures.up(1)
        runCurrent()
        assertTrue(rig.last.isEmpty())

        val quiet = Rig(this, KeyboardSettings(letterLayoutId = "es_qwerty", gestureTrail = false))
        quiet.glide("hola", lift = false)
        assertEquals(PressMode.GESTURE, quiet.last.single().mode)
        assertEquals(0, quiet.last.single().trail.size)
        quiet.gestures.up(1)
    }

    @Test
    fun `other fingers are ignored while one glides`() = runTest {
        val rig = Rig(this)
        rig.glide("hola", lift = false)
        val q = rig.center("q")
        rig.gestures.down(2, q.first, q.second)
        rig.gestures.up(2)
        assertEquals(1, rig.last.size)
        rig.gestures.up(1)
        runCurrent()
        assertEquals("hola ", rig.editor.text.toString())
    }

    @Test
    fun `a second finger already down means no gesture`() = runTest {
        val rig = Rig(this)
        val q = rig.center("q")
        rig.gestures.down(5, q.first, q.second)
        rig.glide("hola", id = 6L)
        rig.gestures.up(5)
        runCurrent()
        assertFalse(rig.editor.text.toString().startsWith("hola"))
    }

    @Test
    fun `gestures start only on letter keys of the letters page`() = runTest {
        val rig = Rig(this)
        val space = rig.geometry.keys.first { (it.key as? ActionKey)?.action == KeyAction.SPACE }
        rig.gestures.down(1, space.left + 5f, (space.top + space.bottom) / 2)
        repeat(20) {
            rig.gestures.move(
                1,
                space.left + 5f + 15f * (it + 1),
                (space.top + space.bottom) / 2
            )
        }
        assertTrue(rig.last.none { it.mode == PressMode.GESTURE })
        rig.gestures.cancel(1)

        rig.logic.showPage(Page.SYMBOLS_1)
        val h = rig.center("h")
        rig.gestures.down(2, h.first, h.second)
        rig.gestures.move(2, h.first + 300f, h.second)
        assertTrue(rig.last.none { it.mode == PressMode.GESTURE })
        rig.gestures.cancel(2)
    }

    @Test
    fun `a held key opens its alternatives instead of starting a gesture`() = runTest {
        val rig = Rig(this)
        val a = rig.center("a")
        rig.gestures.down(1, a.first, a.second)
        testScheduler.advanceTimeBy(400)
        testScheduler.runCurrent()
        rig.gestures.move(1, a.first + 400f, a.second)
        assertTrue(rig.last.none { it.mode == PressMode.GESTURE })
        rig.gestures.cancel(1)
    }

    @Test
    fun `changing the geometry forgets the cached keyboard`() = runTest {
        val rig = Rig(this)
        rig.glide("hola")
        rig.gestures.geometry = KeyGeometry(
            rig.controller.layoutFor(Page.LETTERS, rig.controller.settings.value),
            width = 2000f,
            rowHeight = 200f,
            gapX = 8f,
            gapY = 8f
        )
        rig.gestures.geometry = rig.gestures.geometry
        assertEquals("hola ", rig.editor.text.toString())
    }
}
