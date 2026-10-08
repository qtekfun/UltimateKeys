// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrivacyStateTest {
    private val text = 0x1
    private val password = 0x1 or 0x80
    private val flag = PrivacyRules.IME_FLAG_NO_PERSONALIZED_LEARNING

    @Test
    fun `starts not private`() {
        val state = PrivacyState()
        assertFalse(state.isPrivate)
        assertEquals(PrivacyDecision.Off, state.state.value)
    }

    @Test
    fun `follows the focused field`() {
        val state = PrivacyState()
        state.onStartInput(password, 0)
        assertEquals(PrivacyReason.PASSWORD, state.state.value.reason)
        state.onStartInput(text, flag)
        assertEquals(PrivacyReason.NO_PERSONALIZED_LEARNING, state.state.value.reason)
        state.onStartInput(text, 0)
        assertFalse(state.isPrivate)
    }

    @Test
    fun `the manual switch can be turned on and off`() {
        val state = PrivacyState()
        state.onStartInput(text, 0)
        state.toggleManual()
        assertEquals(PrivacyReason.MANUAL, state.state.value.reason)
        state.toggleManual()
        assertFalse(state.isPrivate)
    }

    @Test
    fun `a field that forces private mode cannot be switched off`() {
        val state = PrivacyState()
        state.onStartInput(password, 0)
        assertFalse(state.canToggle)
        state.toggleManual()
        assertEquals(PrivacyReason.PASSWORD, state.state.value.reason)
        state.onStartInput(text, 0)
        assertTrue(state.canToggle)
        assertFalse(state.isPrivate, "the ignored toggle must not linger")
    }

    @Test
    fun `manual mode ends when the keyboard closes if so configured`() {
        val state = PrivacyState(ManualDuration.UNTIL_KEYBOARD_CLOSES)
        state.onStartInput(text, 0)
        state.toggleManual()
        state.onKeyboardClosed()
        state.onStartInput(text, 0)
        assertFalse(state.isPrivate)
    }

    @Test
    fun `manual mode survives closing the keyboard if so configured`() {
        val state = PrivacyState(ManualDuration.UNTIL_TURNED_OFF)
        state.onStartInput(text, 0)
        state.toggleManual()
        state.onKeyboardClosed()
        assertTrue(state.isPrivate, "still private while the keyboard is hidden")
        state.onStartInput(text, 0)
        assertEquals(PrivacyReason.MANUAL, state.state.value.reason)
    }

    @Test
    fun `the duration can be changed while running`() {
        val state = PrivacyState(ManualDuration.UNTIL_TURNED_OFF)
        state.onStartInput(text, 0)
        state.toggleManual()
        state.manualDuration = ManualDuration.UNTIL_KEYBOARD_CLOSES
        state.onKeyboardClosed()
        assertFalse(state.isPrivate)
    }

    @Test
    fun `closing the keyboard forgets the field`() {
        val state = PrivacyState()
        state.onStartInput(password, 0)
        state.onKeyboardClosed()
        assertFalse(state.isPrivate)
        assertTrue(state.canToggle)
    }
}
