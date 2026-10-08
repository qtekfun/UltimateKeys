// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import com.qtekfun.ultimatekeys.core.FakeSettingsRepository
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.layouts.CharKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardControllerTest {
    @Test
    fun `settings drive the typing options and locale`() = runTest {
        val repo = FakeSettingsRepository()
        val logic = InputLogic()
        val controller = KeyboardController(logic, repo, backgroundScope, null) {}
        runCurrent()
        assertEquals("es", logic.locale.language)
        repo.update { it.copy(letterLayoutId = "en_qwerty", doubleSpacePeriod = false) }
        runCurrent()
        assertEquals("en", logic.locale.language)
        assertEquals(false, logic.options.doubleSpacePeriod)
        assertEquals("en_qwerty", controller.settings.value.letterLayoutId)
    }

    @Test
    fun `layout per page and number row`() = runTest {
        val controller =
            KeyboardController(InputLogic(), FakeSettingsRepository(), backgroundScope, null) {}
        val s = KeyboardSettings()
        val letters = controller.layoutFor(Page.LETTERS, s)
        val withRow = controller.layoutFor(Page.LETTERS, s.copy(numberRow = true))
        assertEquals(letters.rows.size + 1, withRow.rows.size)
        assertEquals("symbols_1", controller.layoutFor(Page.SYMBOLS_1, s).id)
        assertEquals("symbols_2", controller.layoutFor(Page.SYMBOLS_2, s).id)
        assertEquals("numeric", controller.layoutFor(Page.NUMERIC, s).id)
        assertEquals("phone", controller.layoutFor(Page.PHONE, s).id)
        assertTrue(letters.rows.flatMap { it.keys }.any { it is CharKey && it.label == "ñ" })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionWiringTest {
    @Test
    fun `tapping a strip slot commits the suggestion and learning follows settings`() = runTest {
        val engine = com.qtekfun.ultimatekeys.ime.suggest.FakeEngine(
            suggestions = { _, _ -> listOf(com.qtekfun.ultimatekeys.engine.Suggestion("hola", 9)) }
        )
        val logic = InputLogic()
        val editor = com.qtekfun.ultimatekeys.ime.logic.FakeEditorConnection()
        val controller = KeyboardController(
            logic,
            FakeSettingsRepository(),
            backgroundScope,
            null,
            engine,
            kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        ) {}
        runCurrent()
        logic.onStartInput(
            editor,
            com.qtekfun.ultimatekeys.ime.logic.EditorContext.from(
                android.text.InputType.TYPE_CLASS_TEXT,
                0
            ),
            restarting = false,
            initialCursor = 0
        )
        "hol".forEach { logic.onText(it.toString()) }
        runCurrent()
        assertEquals(listOf("", "hola", ""), controller.suggestions.state.value.slots)
        controller.onSuggestionTapped(0)
        assertEquals("hol", editor.text.toString())
        controller.onSuggestionTapped(1)
        assertEquals("hola ", editor.text.toString())
        runCurrent()
        assertEquals("hola", engine.learned.last().first)
    }
}
