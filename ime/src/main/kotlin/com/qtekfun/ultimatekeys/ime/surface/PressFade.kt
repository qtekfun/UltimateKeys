// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

/**
 * How strongly each key shows as pressed: it is full the moment a finger lands (so typing never
 * feels late) and fades out over [FADE_MS] after the finger lifts, so a key settles back instead
 * of snapping. Main thread only; [update] runs once per drawn frame.
 */
class PressFade {
    private val levels = HashMap<PlacedKey, Float>()
    private var lastNanos = 0L

    /** True while some released key is still fading, so the surface must keep drawing frames. */
    val animating: Boolean get() = levels.values.any { it < FULL }

    fun level(key: PlacedKey): Float = levels[key] ?: 0f

    fun update(pressed: Set<PlacedKey>, frameNanos: Long) {
        val elapsedMs = if (lastNanos == 0L || levels.isEmpty()) {
            0f
        } else {
            (frameNanos - lastNanos) / NANOS_PER_MS
        }
        lastNanos = frameNanos
        val step = maxOf(elapsedMs, MIN_STEP_MS) / FADE_MS
        val it = levels.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.key in pressed) continue
            val next = entry.value - step
            if (next <= 0f) it.remove() else entry.setValue(next)
        }
        pressed.forEach { levels[it] = FULL }
    }

    private companion object {
        const val FULL = 1f
        const val FADE_MS = 110f
        const val MIN_STEP_MS = 1f
        const val NANOS_PER_MS = 1_000_000f
    }
}
