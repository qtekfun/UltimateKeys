// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.gesture

import com.qtekfun.ultimatekeys.gesture.GestureContext
import com.qtekfun.ultimatekeys.gesture.GestureDecoder
import com.qtekfun.ultimatekeys.gesture.harness.NoiseProfile
import com.qtekfun.ultimatekeys.gesture.harness.SyntheticGestureGenerator
import java.io.File
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Task 10.7: the candidate list must be ready in under 50 ms (p95) after the finger lifts. Measured on the
 * JVM over the full shipped vocabulary (both languages), with the language context, as the keyboard calls it.
 * A phone is slower than the machine running this; docs/gesture/RESULTS.md reports the ratio used for the
 * budget and the steps that keep the work off the main thread.
 */
class GestureLatencyTest {
    @Test
    fun `candidates are ready within 50 ms at the 95th percentile`() {
        val vocabulary = GestureRig.vocabulary
        val decoder = GestureDecoder(vocabulary)
        val keyboards = mapOf(
            "es" to GestureRig.keyboard("es_qwerty"),
            "en" to GestureRig.keyboard("en_qwerty")
        )
        val paths = ArrayList<Triple<String, FloatArray, List<String>>>()
        for ((language, locale) in listOf("es" to GestureRig.spanish, "en" to GestureRig.english)) {
            val keyboard = keyboards.getValue(language)
            val generator = SyntheticGestureGenerator(keyboard, NoiseProfile.Typical, seed = SEED)
            GestureRig.words(locale).take(POOL).filterIndexed { i, _ ->
                i % STRIDE == 0
            }.forEach { entry ->
                generator.generate(entry.word)?.let {
                    paths +=
                        Triple(language, it, listOf("de", "la"))
                }
            }
        }
        // Warm the JIT the way a keyboard session would, then measure.
        paths.take(WARM_UP).forEach { (language, path, context) ->
            decoder.decode(
                path,
                keyboards.getValue(language),
                GestureContext(previousWords = context)
            )
        }
        val millis = paths.map { (language, path, context) ->
            val started = System.nanoTime()
            decoder.decode(
                path,
                keyboards.getValue(language),
                GestureContext(previousWords = context)
            )
            (System.nanoTime() - started) / NANOS_PER_MS
        }.sorted()
        val p50 = millis[millis.size / 2]
        val p95 = millis[(millis.size * P95).toInt()]
        val p99 = millis[(millis.size * P99).toInt()]
        val summary = String.format(
            Locale.ROOT,
            "gestures=%d vocabulary=%d p50=%.2fms p95=%.2fms p99=%.2fms max=%.2fms",
            millis.size,
            vocabulary.size,
            p50,
            p95,
            p99,
            millis.last()
        )
        File(System.getProperty("user.dir"), "build/gesture-latency.txt").writeText(summary + "\n")
        assertTrue(p95 < BUDGET_MS, summary)
    }

    private companion object {
        const val SEED = 424_242L
        const val POOL = 20_000
        const val STRIDE = 20
        const val WARM_UP = 300
        const val NANOS_PER_MS = 1_000_000.0
        const val P95 = 0.95
        const val P99 = 0.99
        const val BUDGET_MS = 50.0
    }
}
