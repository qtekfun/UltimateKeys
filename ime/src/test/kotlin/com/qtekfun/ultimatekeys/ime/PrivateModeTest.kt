// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import android.text.InputType
import com.qtekfun.ultimatekeys.core.FakeSettingsRepository
import com.qtekfun.ultimatekeys.engine.Suggestion
import com.qtekfun.ultimatekeys.ime.logic.EditorContext
import com.qtekfun.ultimatekeys.ime.logic.FakeEditorConnection
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.suggest.FakeEngine
import com.qtekfun.ultimatekeys.ime.suggest.PrivacyGuardedEngine
import com.qtekfun.ultimatekeys.privacy.PrivacyReason
import com.qtekfun.ultimatekeys.privacy.PrivacyRules
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateModeTest {
    private class Rig(scope: TestScope) {
        val engine = FakeEngine(
            suggestions = { _, _ -> listOf(Suggestion("hola", 9)) }
        )
        val logic = InputLogic()
        val editor = FakeEditorConnection()
        val controller = KeyboardController(
            logic,
            FakeSettingsRepository(),
            scope.backgroundScope,
            null,
            engine,
            StandardTestDispatcher(scope.testScheduler)
        ) {}

        fun focus(inputType: Int, imeOptions: Int = 0) {
            controller.privacy.onStartInput(inputType, imeOptions)
            logic.onStartInput(
                editor,
                EditorContext.from(inputType, imeOptions),
                restarting = false,
                initialCursor = 0
            )
        }

        /** Types "hola " and picks the suggestion, the way a person does. */
        fun typeAndPick(scope: TestScope) {
            "hol".forEach { logic.onText(it.toString()) }
            scope.runCurrent()
            controller.onSuggestionTapped(1)
            logic.onText("x")
            scope.runCurrent()
        }
    }

    @Test
    fun `an ordinary field learns what is typed`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.focus(InputType.TYPE_CLASS_TEXT)
        rig.typeAndPick(this)
        assertTrue(rig.engine.learned.isNotEmpty())
    }

    @Test
    fun `an incognito field never reaches the engine's learning`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.focus(InputType.TYPE_CLASS_TEXT, PrivacyRules.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertEquals(
            PrivacyReason.NO_PERSONALIZED_LEARNING,
            rig.controller.privacy.state.value.reason
        )
        rig.typeAndPick(this)
        assertEquals(emptyList<Pair<String, List<String>>>(), rig.engine.learned)
    }

    @Test
    fun `a manual switch stops learning and switching it off resumes`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.focus(InputType.TYPE_CLASS_TEXT)
        rig.controller.privacy.toggleManual()
        rig.typeAndPick(this)
        assertTrue(rig.engine.learned.isEmpty())
        rig.controller.privacy.toggleManual()
        rig.typeAndPick(this)
        assertFalse(rig.engine.learned.isEmpty())
    }

    @Test
    fun `password fields are private`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.focus(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        rig.typeAndPick(this)
        assertTrue(rig.engine.learned.isEmpty())
    }

    @Test
    fun `the strip button toggles private mode through the controller`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.focus(InputType.TYPE_CLASS_TEXT)
        rig.controller.togglePrivate()
        assertTrue(rig.controller.privacy.isPrivate)
        rig.controller.togglePrivate()
        assertFalse(rig.controller.privacy.isPrivate)
    }

    @Test
    fun `the settings choose how long a manual switch lasts`() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.focus(InputType.TYPE_CLASS_TEXT)
        rig.controller.togglePrivate()
        rig.controller.privacy.onKeyboardClosed()
        assertFalse(rig.controller.privacy.isPrivate, "default: ends when the keyboard closes")
    }

    @Test
    fun `the guard blocks learning even when called directly`() {
        val engine = FakeEngine()
        var private = true
        val guarded = PrivacyGuardedEngine(engine) { private }
        guarded.learn("secret", emptyList(), Locale.ENGLISH)
        assertTrue(engine.learned.isEmpty())
        private = false
        guarded.learn("fine", emptyList(), Locale.ENGLISH)
        assertEquals("fine", engine.learned.single().first)
    }

    @Test
    fun `the guard still answers lookups and explicit dictionary edits`() {
        val engine = FakeEngine(suggestions = { _, _ -> listOf(Suggestion("hola", 9)) })
        val guarded = PrivacyGuardedEngine(engine) { true }
        assertEquals("hola", guarded.suggest(emptyList(), "ho", Locale.ENGLISH).single().word)
        guarded.addToUserDictionary("mi", Locale.ENGLISH)
        assertEquals(listOf("mi"), engine.userWords)
    }

    @Test
    fun `the platform constants match the rules`() {
        assertEquals(
            android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            PrivacyRules.IME_FLAG_NO_PERSONALIZED_LEARNING
        )
        listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ).forEach { assertTrue(PrivacyRules.isPassword(it), it.toString()) }
        listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            InputType.TYPE_CLASS_PHONE
        ).forEach { assertFalse(PrivacyRules.isPassword(it), it.toString()) }
    }
}
