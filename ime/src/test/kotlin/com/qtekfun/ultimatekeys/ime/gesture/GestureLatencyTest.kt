// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.gesture

import com.qtekfun.ultimatekeys.gesture.GestureContext
import com.qtekfun.ultimatekeys.gesture.GestureDecoder
import com.qtekfun.ultimatekeys.gesture.GestureKeyboard
import com.qtekfun.ultimatekeys.gesture.harness.NoiseProfile
import com.qtekfun.ultimatekeys.gesture.harness.SyntheticGestureGenerator
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Task 10.7: the candidate list must be ready in under 50 ms (p95) after the finger lifts. Measured on the
 * JVM over the full shipped vocabulary (both languages), with the language context, as the keyboard calls it.
 * A phone is slower than the machine running this; docs/gesture/RESULTS.md reports the reasoning for the
 * budget and the steps that keep the work off the main thread.
 */
class GestureLatencyTest {
    private class Gesture(val keyboard: GestureKeyboard, val path: FloatArray)

    private class Timings(val cpu: List<Double>, val wall: List<Double>) {
        fun cpuAt(q: Double) = cpu[(cpu.size * q).toInt()]

        fun wallAt(q: Double) = wall[(wall.size * q).toInt()]
    }

    private val keyboards = mapOf(
        "es" to GestureRig.keyboard("es_qwerty"),
        "en" to GestureRig.keyboard("en_qwerty")
    )
    private val context = GestureContext(previousWords = listOf("de", "la"))

    private fun gestures(): List<Gesture> = listOf(
        "es" to GestureRig.spanish,
        "en" to GestureRig.english
    ).flatMap { (language, locale) ->
        val keyboard = keyboards.getValue(language)
        val generator = SyntheticGestureGenerator(keyboard, NoiseProfile.Typical, seed = SEED)
        GestureRig.words(locale).take(POOL).filterIndexed { i, _ -> i % STRIDE == 0 }
            .mapNotNull { entry ->
                generator.generate(entry.word)?.let { Gesture(keyboard, it) }
            }
    }

    /** CPU time of this thread is what decoding costs, not what other processes on the machine take. */
    private fun measure(decoder: GestureDecoder, gestures: List<Gesture>): Timings {
        val cpu = ManagementFactory.getThreadMXBean()
        val wall = ArrayList<Double>()
        val used = gestures.map {
            val startedWall = System.nanoTime()
            val started = cpu.currentThreadCpuTime
            decoder.decode(it.path, it.keyboard, context)
            wall += (System.nanoTime() - startedWall) / NANOS_PER_MS
            (cpu.currentThreadCpuTime - started) / NANOS_PER_MS
        }
        return Timings(used.sorted(), wall.sorted())
    }

    @Test
    fun `candidates are ready within 50 ms at the 95th percentile`() {
        val vocabulary = GestureRig.vocabulary
        val decoder = GestureDecoder(vocabulary)
        val gestures = gestures()
        // Warm the JIT the way a keyboard session would, then measure.
        gestures.take(WARM_UP).forEach { decoder.decode(it.path, it.keyboard, context) }
        val timings = measure(decoder, gestures)
        val work = gestures.take(WORK_SAMPLE).map {
            decoder.decodeWithStats(it.path, it.keyboard, context).second
        }
        val summary = String.format(
            Locale.ROOT,
            "gestures=%d vocabulary=%d p50=%.2fms p95=%.2fms p99=%.2fms max=%.2fms " +
                "wall-p95=%.2fms considered=%.0f scored=%.0f",
            gestures.size,
            vocabulary.size,
            timings.cpuAt(P50),
            timings.cpuAt(P95),
            timings.cpuAt(P99),
            timings.cpu.last(),
            timings.wallAt(P95),
            work.map { it.considered }.average(),
            work.map { it.scored }.average()
        )
        File(System.getProperty("user.dir"), "build/gesture-latency.txt").writeText(summary + "\n")
        assertTrue(timings.cpuAt(P95) < BUDGET_MS, summary)
    }

    private companion object {
        const val SEED = 424_242L
        const val POOL = 20_000
        const val STRIDE = 20
        const val WARM_UP = 300
        const val WORK_SAMPLE = 500
        const val NANOS_PER_MS = 1_000_000.0
        const val P50 = 0.5
        const val P95 = 0.95
        const val P99 = 0.99
        const val BUDGET_MS = 50.0
    }
}
