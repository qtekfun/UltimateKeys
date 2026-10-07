// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.core.SettingsRepository
import com.qtekfun.ultimatekeys.ime.logic.InputLogic
import com.qtekfun.ultimatekeys.ime.logic.InputOptions
import com.qtekfun.ultimatekeys.ime.logic.Page
import com.qtekfun.ultimatekeys.ime.surface.LatencyTracker
import com.qtekfun.ultimatekeys.layouts.KeyAction
import com.qtekfun.ultimatekeys.layouts.KeyboardLayout
import com.qtekfun.ultimatekeys.layouts.LayoutRepository
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Connects the surface, the typing logic, the settings and the feedback. Main thread only. */
class KeyboardController(
    val logic: InputLogic,
    private val repository: SettingsRepository,
    private val scope: CoroutineScope,
    private val feedback: Feedback?,
    private val showImePicker: () -> Unit
) {
    val settings: StateFlow<KeyboardSettings> =
        repository.settings.stateIn(scope, SharingStarted.Eagerly, KeyboardSettings())
    val latency = LatencyTracker()

    init {
        repository.settings.onEach(::apply).launchIn(scope)
    }

    private fun apply(s: KeyboardSettings) {
        logic.options = InputOptions(
            autoCapitalize = s.autoCapitalize,
            doubleSpacePeriod = s.doubleSpacePeriod,
            smartPunctuation = s.smartPunctuation
        )
        logic.locale = Locale.forLanguageTag(LayoutRepository.load(s.letterLayoutId).locale ?: "en")
    }

    fun layoutFor(page: Page, settings: KeyboardSettings): KeyboardLayout = when (page) {
        Page.LETTERS -> LayoutRepository.pages(settings.letterLayoutId, settings.numberRow).letters
        Page.SYMBOLS_1 -> LayoutRepository.load("symbols_1")
        Page.SYMBOLS_2 -> LayoutRepository.load("symbols_2")
        Page.NUMERIC -> LayoutRepository.load("numeric")
        Page.PHONE -> LayoutRepository.load("phone")
    }

    fun keyDown(action: KeyAction?) {
        val s = settings.value
        feedback?.keyDown(action, s.hapticIntensity, s.soundVolume)
    }

    fun onText(text: String) = logic.onText(text)

    fun onAction(action: KeyAction) {
        when (action) {
            KeyAction.SHIFT -> logic.onShiftTap()
            KeyAction.DELETE -> logic.onDelete()
            KeyAction.ENTER -> logic.onEnter()
            KeyAction.SPACE -> logic.onSpace()
            KeyAction.GLOBE -> cycleLayout()
            KeyAction.SWITCH_LETTERS -> logic.showPage(Page.LETTERS)
            KeyAction.SWITCH_SYMBOLS -> logic.showPage(Page.SYMBOLS_1)
            KeyAction.SWITCH_SYMBOLS_2 -> logic.showPage(Page.SYMBOLS_2)
        }
    }

    fun onGlobeLongPress() = showImePicker()

    private fun cycleLayout() {
        scope.launch {
            repository.update {
                val ids = LayoutRepository.letterLayoutIds
                val next = ids[(ids.indexOf(it.letterLayoutId) + 1).mod(ids.size)]
                it.copy(letterLayoutId = next)
            }
        }
    }
}
