// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import java.io.File

/**
 * Which cores inference should run on. Phones mix slow and fast cores; running whisper on a slow
 * one makes the whole decode wait for it, so threads go on the fast cores only.
 *
 * @property bigCores indices of the fast cores, empty when the topology is unknown.
 */
data class CpuTopology(val bigCores: List<Int>, val threads: Int) {
    companion object {
        /** More threads than this do not speed up whisper on phones (memory bound). */
        const val MAX_THREADS = 4
        private const val BIG_FRACTION = 0.7

        /**
         * [maxFrequenciesKhz] holds each core's maximum frequency by core index, 0 or negative for
         * a core that could not be read. A core is "big" when it reaches [BIG_FRACTION] of the
         * fastest one.
         */
        fun from(maxFrequenciesKhz: List<Long>, availableProcessors: Int): CpuTopology {
            val fastest = maxFrequenciesKhz.maxOrNull() ?: 0L
            if (fastest <= 0L) {
                return CpuTopology(emptyList(), (availableProcessors / 2).coerceIn(1, MAX_THREADS))
            }
            val big = maxFrequenciesKhz.withIndex()
                .filter { it.value >= fastest * BIG_FRACTION }
                .map { it.index }
            return CpuTopology(big, big.size.coerceIn(1, MAX_THREADS))
        }

        /** Reads the cores' maximum frequencies from sysfs (readable on most, not all, devices). */
        fun system(
            sysfs: File = File("/sys/devices/system/cpu"),
            availableProcessors: Int = Runtime.getRuntime().availableProcessors()
        ): CpuTopology {
            val frequencies = (0 until availableProcessors).map { index ->
                runCatching {
                    File(sysfs, "cpu$index/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
                }.getOrDefault(0L)
            }
            return from(frequencies, availableProcessors)
        }
    }
}
