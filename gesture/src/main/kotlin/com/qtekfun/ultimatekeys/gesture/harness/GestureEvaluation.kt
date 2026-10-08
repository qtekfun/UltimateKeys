// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture.harness

import com.qtekfun.ultimatekeys.gesture.GestureContext
import com.qtekfun.ultimatekeys.gesture.GestureDecoder
import com.qtekfun.ultimatekeys.gesture.GestureKeyboard

/** Hits at the top 1, 3 and 5 of the candidate list, out of [total] words. */
data class Accuracy(val total: Int, val top1: Int, val top3: Int, val top5: Int) {
    val top1Rate: Double get() = rate(top1)
    val top3Rate: Double get() = rate(top3)
    val top5Rate: Double get() = rate(top5)

    private fun rate(hits: Int) = if (total == 0) 0.0 else hits.toDouble() / total

    operator fun plus(other: Accuracy) =
        Accuracy(total + other.total, top1 + other.top1, top3 + other.top3, top5 + other.top5)

    companion object {
        val Empty = Accuracy(0, 0, 0, 0)
    }
}

/** The outcome of an evaluation run. */
data class EvaluationReport(
    val profile: NoiseProfile,
    val overall: Accuracy,
    /** Accuracy by number of letters traced: "2-3", "4-5", "6+". */
    val byLength: Map<String, Accuracy>,
    /** Words that could not be traced on this keyboard (not evaluated). */
    val skipped: Int,
    /** A sample of the words missed at top 3 with what was returned, for inspection. */
    val misses: List<Miss>
)

data class Miss(val word: String, val returned: List<String>)

/**
 * Offline evaluation: makes a synthetic gesture for every word of a list, decodes it and checks whether
 * the word is among the first candidates. It measures the decoder, not people: see docs/gesture/RESULTS.md
 * for what that does and does not say.
 */
class GestureEvaluation(
    private val decoder: GestureDecoder,
    private val keyboard: GestureKeyboard
) {
    fun run(
        words: List<String>,
        profile: NoiseProfile,
        seed: Long = DEFAULT_SEED,
        context: GestureContext = GestureContext(),
        maxMisses: Int = DEFAULT_MAX_MISSES
    ): EvaluationReport {
        val generator = SyntheticGestureGenerator(keyboard, profile, seed)
        val buckets = linkedMapOf(
            "2-3" to Accuracy.Empty,
            "4-5" to Accuracy.Empty,
            "6+" to Accuracy.Empty
        )
        var overall = Accuracy.Empty
        var skipped = 0
        val misses = ArrayList<Miss>()
        for (word in words) {
            val path = generator.generate(word)
            if (path == null) {
                skipped++
                continue
            }
            val ranked = decoder.decode(path, keyboard, context).map { it.word }
            val rank = ranked.indexOfFirst { it.equals(word, ignoreCase = true) }
            val result = Accuracy(
                total = 1,
                top1 = if (rank == 0) 1 else 0,
                top3 = if (rank in 0..TOP_3_LAST) 1 else 0,
                top5 = if (rank in 0..TOP_5_LAST) 1 else 0
            )
            overall += result
            val bucket = bucketOf(word)
            buckets[bucket] = buckets.getValue(bucket) + result
            if (rank !in 0..TOP_3_LAST &&
                misses.size < maxMisses
            ) {
                misses += Miss(word, ranked.take(TOP_3))
            }
        }
        return EvaluationReport(profile, overall, buckets, skipped, misses)
    }

    private fun bucketOf(word: String): String {
        val letters = word.count { it.isLetter() }
        return when {
            letters <= SHORT -> "2-3"
            letters <= MEDIUM -> "4-5"
            else -> "6+"
        }
    }

    private companion object {
        const val DEFAULT_SEED = 20_261_008L
        const val DEFAULT_MAX_MISSES = 25
        const val SHORT = 3
        const val TOP_3 = 3
        const val TOP_3_LAST = 2
        const val TOP_5_LAST = 4
        const val MEDIUM = 5
    }
}
