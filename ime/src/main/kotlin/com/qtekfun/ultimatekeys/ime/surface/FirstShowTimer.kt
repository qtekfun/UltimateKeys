// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

/**
 * Times the keyboard's first show: from the service being created to the first frame of the keys
 * being drawn (SPEC section 12: under 300 ms). Local only; debug builds log the result once.
 */
class FirstShowTimer(private val clockNanos: () -> Long, private val log: (String) -> Unit) {
    private var created = -1L
    private var reported = false

    fun markCreated() {
        if (created < 0L) created = clockNanos()
    }

    /** Called on each draw; only the first one after [markCreated] is reported. */
    fun markDrawn() {
        if (reported || created < 0L) return
        reported = true
        val ms = (clockNanos() - created) / NANOS_PER_MS
        log("first-show $ms ms (service created to first frame)")
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
