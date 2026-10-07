// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.surface

import kotlin.math.ceil

/**
 * Keeps the last [capacity] key-to-commit latencies (nanoseconds) to report percentiles.
 * Local only: nothing is sent anywhere; debug builds log the summary.
 */
class LatencyTracker(private val capacity: Int = 512) {
    private val samples = LongArray(capacity)
    private var count = 0
    private var next = 0

    @Synchronized
    fun record(nanos: Long) {
        samples[next] = nanos
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    @Synchronized
    fun percentileMs(percentile: Double): Double? {
        if (count == 0) return null
        val sorted = samples.copyOf(count).also { it.sort() }
        val rank = ceil(percentile / PERCENT * count).toInt().coerceIn(1, count)
        return sorted[rank - 1] / NANOS_PER_MS
    }

    @Synchronized
    fun size(): Int = count

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
        const val PERCENT = 100.0
    }
}
