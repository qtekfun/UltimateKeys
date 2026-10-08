// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import kotlin.math.sqrt

/** Small, pure transformations of the recorded audio. */
object AudioPrep {
    /** whisper.cpp refuses input shorter than one second; shorter audio is padded with silence. */
    const val MIN_INPUT_MS = 1_200

    fun samplesFor(milliseconds: Int): Int = SAMPLE_RATE_HZ / MILLIS_PER_SECOND * milliseconds

    fun padToMinimum(samples: FloatArray): FloatArray {
        val minimum = samplesFor(MIN_INPUT_MS)
        return if (samples.size >= minimum) samples else samples.copyOf(minimum)
    }

    /** Root mean square of [count] samples of [samples] from [start]; 0 for an empty range. */
    fun rms(samples: FloatArray, start: Int = 0, count: Int = samples.size - start): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in start until start + count) sum += samples[i] * samples[i]
        return sqrt(sum / count).toFloat()
    }

    private const val MILLIS_PER_SECOND = 1_000
}
