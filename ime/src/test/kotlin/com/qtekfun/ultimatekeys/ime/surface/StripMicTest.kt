// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import com.qtekfun.ultimatekeys.core.FakeSettingsRepository
import com.qtekfun.ultimatekeys.ime.KeyboardController
import com.qtekfun.ultimatekeys.ime.RecordingActions
import com.qtekfun.ultimatekeys.ime.ScriptedTranscriber
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.ime.voice.DictationHost
import com.qtekfun.ultimatekeys.voice.DictationConfig
import com.qtekfun.ultimatekeys.voice.DictationController
import com.qtekfun.ultimatekeys.voice.DictationError
import com.qtekfun.ultimatekeys.voice.DictationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StripMicLayoutTest {
    @Test
    fun `the microphone takes a square at the right`() {
        val layout = StripLayout(width = 340f, height = 40f, showToggle = false, showMic = true)
        assertEquals(40f, layout.micWidth)
        assertTrue(layout.isMic(300f))
        assertFalse(layout.isMic(299f))
        assertEquals(100f, layout.cellWidth)
        assertEquals(listOf(0, 1, 2), listOf(1f, 150f, 299f).map(layout::slotAt))
    }

    @Test
    fun `both buttons leave the rest to the slots`() {
        val layout = StripLayout(width = 380f, height = 40f, showToggle = true, showMic = true)
        assertEquals(100f, layout.cellWidth)
        assertTrue(layout.isToggle(10f))
        assertTrue(layout.isMic(370f))
        assertFalse(layout.isMic(100f))
    }

    @Test
    fun `without the microphone nothing is a microphone tap`() {
        val layout = StripLayout(width = 340f, height = 40f, showToggle = false)
        assertFalse(layout.isMic(339f))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StripMicGestureTest {
    @Test
    fun `tapping the microphone in the bar opens dictation`() = runTest {
        val host = DictationHost(
            DictationController(
                scope = backgroundScope,
                transcriber = ScriptedTranscriber(),
                microphone = { error("not reached") },
                models = { null },
                permission = { false },
                config = { DictationConfig() },
                onResult = {}
            ),
            RecordingActions(),
            emptyFlow(),
            backgroundScope
        )
        val controller = KeyboardController(
            InputLogic(),
            FakeSettingsRepository(),
            backgroundScope,
            null,
            dictation = host
        ) {}
        runCurrent()
        val gestures = SurfaceGestures(controller, backgroundScope) {}
        gestures.geometry = KeyGeometry(
            controller.layoutFor(Page.LETTERS, controller.settings.value),
            width = 1000f,
            rowHeight = 100f,
            gapX = 4f,
            gapY = 4f,
            top = 40f
        )
        gestures.stripMicVisible = true
        // The bar is above the keys: a press at the far right, in the bar.
        gestures.down(1, 990f, 5f)
        gestures.up(1, 990f)
        assertEquals(
            DictationState.Failed(DictationError.NO_PERMISSION),
            host.state.value
        )
    }
}
