// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrivacyRulesTest {
    private val text = 0x1
    private val number = 0x2
    private val phone = 0x3
    private val flag = PrivacyRules.IME_FLAG_NO_PERSONALIZED_LEARNING

    @Test
    fun `an ordinary text field is not private`() {
        assertEquals(
            PrivacyDecision.Off,
            PrivacyRules.shouldBePrivate(text, 0, manualToggle = false)
        )
        assertNull(PrivacyRules.automaticReason(text or 0x20, 0))
    }

    @Test
    fun `the app opt-out flag makes it private`() {
        val d = PrivacyRules.shouldBePrivate(text, flag or 0x6, false)
        assertEquals(PrivacyDecision(true, PrivacyReason.NO_PERSONALIZED_LEARNING), d)
    }

    @Test
    fun `every password variation makes it private`() {
        listOf(text or 0x80, text or 0x90, text or 0xe0, number or 0x10).forEach {
            assertTrue(PrivacyRules.isPassword(it), "0x${it.toString(16)}")
            assertEquals(
                PrivacyDecision(true, PrivacyReason.PASSWORD),
                PrivacyRules.shouldBePrivate(it, 0, false)
            )
        }
    }

    @Test
    fun `lookalike input types are not passwords`() {
        listOf(
            text or 0x20, // email subject
            text or 0xa0, // email address
            text or 0xd0, // person name
            number, // plain number
            number or 0x2000, // decimal flag
            phone or 0x10, // class is not text or number
            0 // no class
        ).forEach { assertFalse(PrivacyRules.isPassword(it), "0x${it.toString(16)}") }
    }

    @Test
    fun `a password variation with a text flag set is still a password`() {
        assertTrue(PrivacyRules.isPassword(text or 0x80 or 0x8000 or 0x20000))
    }

    @Test
    fun `the manual switch makes it private with the manual reason`() {
        assertEquals(
            PrivacyDecision(true, PrivacyReason.MANUAL),
            PrivacyRules.shouldBePrivate(text, 0, manualToggle = true)
        )
    }

    @Test
    fun `automatic reasons win over the manual switch`() {
        assertEquals(
            PrivacyReason.PASSWORD,
            PrivacyRules.shouldBePrivate(text or 0x80, 0, true).reason
        )
        assertEquals(
            PrivacyReason.NO_PERSONALIZED_LEARNING,
            PrivacyRules.shouldBePrivate(text or 0x80, flag, true).reason
        )
    }
}
