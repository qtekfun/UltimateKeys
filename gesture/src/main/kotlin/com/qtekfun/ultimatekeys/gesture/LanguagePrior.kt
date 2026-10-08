// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

import kotlin.math.pow

/**
 * How likely the next word is in each language, for people who write Spanish and English in the same
 * message. It follows the model of the suggestion engine's mixed-language mode: the words just written are
 * evidence for the languages that accept them (a word valid in only one language counts fully, a word
 * valid in both says nothing), recent words count more, and the evidence is shrunk towards a prior that
 * favours the primary language so that one foreign word never flips the decision.
 *
 * `weight(l) = (priorMass * prior(l) + evidence(l)) / (priorMass + totalEvidence)`; weights sum to 1.
 */
class LanguagePrior(
    private val vocabulary: GestureVocabulary,
    private val primary: Int = 0,
    private val primaryPrior: Double = DEFAULT_PRIMARY_PRIOR,
    private val contextWords: Int = DEFAULT_CONTEXT_WORDS,
    private val recencyDecay: Double = DEFAULT_DECAY,
    private val priorMass: Double = 1.0
) {
    /** The weight of every language of the vocabulary for the words typed before, oldest first. */
    fun weights(context: List<String>): DoubleArray {
        val n = vocabulary.languages.size
        val prior = DoubleArray(n) {
            if (n ==
                1
            ) {
                1.0
            } else if (it == primary) {
                primaryPrior
            } else {
                (1.0 - primaryPrior) / (n - 1)
            }
        }
        if (n < 2) return prior
        val evidence = DoubleArray(n)
        var total = 0.0
        val recent = context.filter { it.isNotBlank() }.takeLast(contextWords).asReversed()
        recent.forEachIndexed { age, word ->
            val accepting = (0 until n).filter { vocabulary.contains(it, word) }
            if (accepting.isEmpty() || accepting.size == n) return@forEachIndexed
            val share = recencyDecay.pow(age)
            accepting.forEach { evidence[it] += share / accepting.size }
            total += share
        }
        val denominator = priorMass + total
        return DoubleArray(n) { (priorMass * prior[it] + evidence[it]) / denominator }
    }

    companion object {
        const val DEFAULT_PRIMARY_PRIOR = 0.75
        const val DEFAULT_CONTEXT_WORDS = 4
        const val DEFAULT_DECAY = 0.7
    }
}
