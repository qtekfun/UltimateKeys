// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import android.text.InputType
import com.qtekfun.ultimatekeys.clipboard.MemoryClipStore
import com.qtekfun.ultimatekeys.core.FakeSettingsRepository
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.panels.PanelKind
import com.qtekfun.ultimatekeys.ime.surface.GestureMetrics
import com.qtekfun.ultimatekeys.ime.surface.KeyGeometry
import com.qtekfun.ultimatekeys.ime.surface.StripLayout
import com.qtekfun.ultimatekeys.ime.surface.SurfaceGestures
import com.qtekfun.ultimatekeys.layouts.KeyAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PanelWiringTest {
    private class Rig(scope: TestScope) {
        val repo = FakeSettingsRepository()
        val store = MemoryClipStore()
        val controller = KeyboardController(
            InputLogic(),
            repo,
            scope.backgroundScope,
            null,
            clipStore = store
        ) {}

        init {
            scope.runCurrent()
        }
    }

    @Test
    fun `the emoji key and the strip button open the panels`() = runTest {
        val rig = Rig(this)
        assertTrue(rig.controller.features.emoji)
        assertEquals(PanelKind.NONE, rig.controller.panels.kind.value)
        rig.controller.onAction(KeyAction.EMOJI)
        assertEquals(PanelKind.EMOJI, rig.controller.panels.kind.value)
        rig.controller.panels.close()
        rig.controller.openClipboard()
        assertEquals(PanelKind.CLIPBOARD, rig.controller.panels.kind.value)
    }

    @Test
    fun `copies are stored with the settings policy`() = runTest {
        val rig = Rig(this)
        assertTrue(rig.controller.clipboard.onClip("hello", false))
        assertEquals(listOf("hello"), store(rig))
    }

    @Test
    fun `copies are not stored in private mode, for password fields or when switched off`() =
        runTest {
            val rig = Rig(this)
            rig.controller.privacy.onStartInput(InputType.TYPE_CLASS_TEXT or PASSWORD, 0)
            assertFalse(rig.controller.clipboard.onClip("secret", false))
            rig.controller.privacy.onStartInput(InputType.TYPE_CLASS_TEXT, 0)
            rig.controller.privacy.toggleManual()
            assertFalse(rig.controller.clipboard.onClip("manual private", false))
            rig.controller.privacy.toggleManual()
            assertFalse(rig.controller.clipboard.onClip("flagged", true))
            rig.repo.update { it.copy(clipboardEnabled = false) }
            runCurrent()
            assertFalse(rig.controller.clipboard.onClip("off", false))
            assertEquals(emptyList<String>(), store(rig))
        }

    @Test
    fun `retention and the limit come from the settings`() = runTest {
        val rig = Rig(this)
        rig.repo.update { it.copy(clipboardMaxItems = 5, clipboardRetention = "forever") }
        runCurrent()
        repeat(8) { rig.controller.clipboard.onClip("clip $it", false) }
        assertEquals(5, store(rig).size)
    }

    @Test
    fun `taps on the strip tools open the panels and the private toggle still works`() = runTest {
        val rig = Rig(this)
        val gestures = SurfaceGestures(rig.controller, backgroundScope) {}
        gestures.geometry = KeyGeometry(
            rig.controller.layoutFor(
                com.qtekfun.ultimatekeys.ime.logic.Page.LETTERS,
                rig.controller.settings.value
            ),
            width = 400f,
            rowHeight = 50f,
            top = 40f
        )
        gestures.metrics =
            GestureMetrics(slop = 10f, dragStep = 10f, chooserMinCell = 10f, chooserHeight = 10f)
        gestures.stripToggleVisible = true
        gestures.stripToolsVisible = true
        // Toggle 0..40, clipboard 40..80, emoji 80..120, then the slots.
        gestures.down(1, 60f, 10f)
        gestures.up(1, 60f)
        assertEquals(PanelKind.CLIPBOARD, rig.controller.panels.kind.value)
        rig.controller.panels.close()
        gestures.down(1, 100f, 10f)
        gestures.up(1, 100f)
        assertEquals(PanelKind.EMOJI, rig.controller.panels.kind.value)
        gestures.down(1, 20f, 10f)
        gestures.up(1, 20f)
        assertTrue(rig.controller.privacy.isPrivate)
    }

    @Test
    fun `the strip layout reserves the tools before the slots`() {
        val layout = StripLayout(width = 420f, height = 40f, showToggle = true, extraTools = 2)
        assertEquals(120f, layout.toolsWidth)
        assertEquals(100f, layout.cellWidth)
        assertEquals(-1, layout.extraToolAt(10f))
        assertEquals(0, layout.extraToolAt(50f))
        assertEquals(1, layout.extraToolAt(110f))
        assertEquals(-1, layout.extraToolAt(125f))
        assertEquals(0, layout.slotAt(125f))
        assertEquals(2, layout.slotAt(419f))
        assertEquals(40f, StripLayout(420f, 40f, showToggle = true).toolsWidth)
    }

    private suspend fun store(rig: Rig) = rig.store.observe().first().map { it.text }

    private companion object {
        const val PASSWORD = 0x80
    }
}
