// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.gesture

import com.qtekfun.ultimatekeys.gesture.GestureContext
import com.qtekfun.ultimatekeys.gesture.GestureDecoder
import com.qtekfun.ultimatekeys.gesture.harness.GestureEvaluation
import com.qtekfun.ultimatekeys.gesture.harness.NoiseProfile
import com.qtekfun.ultimatekeys.gesture.harness.SyntheticGestureGenerator
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Gesture typing with six enabled languages at once (the most the Languages screen allows): the vocabulary stays
 * bounded, words of every script are found on their own layout, and the decode time stays inside the budget of the
 * two-language case.
 */
class ManyLanguagesGestureTest {
    private val tags = arrayOf("fr", "es", "en-US", "de", "ru", "el")
    private val layouts = mapOf(
        "fr" to "fr_azerty",
        "de" to "de_qwertz",
        "ru" to "ru_jcuken",
        "el" to "el_greek"
    )

    private val vocabulary by lazy { GestureRig.vocabularyOf(*tags) }

    @Test
    fun `the vocabulary holds every language and stays bounded`() {
        assertEquals(tags.toList(), vocabulary.languages)
        assertTrue(vocabulary.size <= tags.size * GestureSupport.MAX_WORDS_PER_LANGUAGE)
        assertTrue(vocabulary.size > tags.size * 30_000, "only ${vocabulary.size} words")
    }

    @Test
    fun `common words of each script are found on their own layout`() {
        val decoder = GestureDecoder(vocabulary)
        for ((tag, layout) in layouts) {
            val locale = Locale.forLanguageTag(tag)
            val keyboard = GestureRig.keyboard(layout)
            val words = GestureRig.words(locale).take(COMMON).map { it.word }
                .filterIndexed { i, _ -> i % SAMPLE_EVERY == 0 }
            val report = GestureEvaluation(decoder, keyboard).run(words, NoiseProfile.Typical)
            assertTrue(report.overall.top3Rate >= TARGET, "$tag: $report")
        }
    }

    @Test
    fun `decoding with six languages stays within the 50 ms budget`() {
        val decoder = GestureDecoder(vocabulary)
        val gestures = layouts.flatMap { (tag, layout) ->
            val keyboard = GestureRig.keyboard(layout)
            val generator = SyntheticGestureGenerator(keyboard, NoiseProfile.Typical, seed = SEED)
            GestureRig.words(Locale.forLanguageTag(tag)).take(POOL).filterIndexed { i, _ ->
                i %
                    STRIDE ==
                    0
            }
                .mapNotNull { w -> generator.generate(w.word)?.let { keyboard to it } }
        }
        val context = GestureContext(previousWords = listOf("de", "la"))
        gestures.take(WARM_UP).forEach { (k, p) -> decoder.decode(p, k, context) }
        val cpu = ManagementFactory.getThreadMXBean()
        val used = gestures.map { (k, p) ->
            val started = cpu.currentThreadCpuTime
            decoder.decode(p, k, context)
            (cpu.currentThreadCpuTime - started) / NANOS_PER_MS
        }.sorted()
        val p95 = used[(used.size * P95).toInt()]
        val summary = String.format(
            Locale.ROOT,
            "languages=%d vocabulary=%d gestures=%d p50=%.2fms p95=%.2fms max=%.2fms",
            tags.size,
            vocabulary.size,
            used.size,
            used[used.size / 2],
            p95,
            used.last()
        )
        File(System.getProperty("user.dir"), "build/gesture-latency-six-languages.txt")
            .writeText(summary + "\n")
        assertTrue(p95 < BUDGET_MS, summary)
    }

    private companion object {
        const val COMMON = 5000
        const val SAMPLE_EVERY = 25
        const val TARGET = 0.85
        const val SEED = 424_242L
        const val POOL = 20_000
        const val STRIDE = 40
        const val WARM_UP = 200
        const val NANOS_PER_MS = 1_000_000.0
        const val P95 = 0.95
        const val BUDGET_MS = 50.0
    }
}
