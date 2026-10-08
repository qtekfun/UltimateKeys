// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.privacy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the keyboard is private right now. One instance per keyboard process; every place that
 * would learn, store or log what is typed asks [isPrivate] first.
 */
class PrivacyState(manualDuration: ManualDuration = ManualDuration.UNTIL_KEYBOARD_CLOSES) {
    private val mutable = MutableStateFlow(PrivacyDecision.Off)
    val state: StateFlow<PrivacyDecision> = mutable.asStateFlow()

    private var inputType = 0
    private var imeOptions = 0
    private var manual = false

    var manualDuration: ManualDuration = manualDuration

    val isPrivate: Boolean get() = mutable.value.isPrivate

    /** A field got the keyboard (also when the same field restarts). */
    fun onStartInput(inputType: Int, imeOptions: Int) {
        this.inputType = inputType
        this.imeOptions = imeOptions
        update()
    }

    /** The keyboard was hidden; a manual switch-on may end here depending on the setting. */
    fun onKeyboardClosed() {
        if (manualDuration == ManualDuration.UNTIL_KEYBOARD_CLOSES) manual = false
        inputType = 0
        imeOptions = 0
        update()
    }

    /** Flips the manual switch. Does nothing when the field forces private mode. */
    fun toggleManual() {
        if (PrivacyRules.automaticReason(inputType, imeOptions) != null) return
        manual = !manual
        update()
    }

    /** True when the person can turn private mode off (it is not forced by the field). */
    val canToggle: Boolean get() = PrivacyRules.automaticReason(inputType, imeOptions) == null

    private fun update() {
        mutable.value = PrivacyRules.shouldBePrivate(inputType, imeOptions, manual)
    }
}
