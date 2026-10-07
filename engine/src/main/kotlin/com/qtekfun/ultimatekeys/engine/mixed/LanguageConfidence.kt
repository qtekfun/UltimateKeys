// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine.mixed

import kotlin.math.pow

/** Tuning knobs of [MixedSuggestionEngine]. The defaults favour the primary language. */
data class MixedConfig(
    /** How many of the most recent context words are examined to infer the language. */
    val contextWords: Int = 4,
    /** Prior probability of the primary language; the rest is split evenly among the others. */
    val primaryPrior: Double = 0.75,
    /** Each older context word counts this fraction of the next newer one. */
    val recencyDecay: Double = 0.7,
    /** How much the prior is worth, in units of one fresh, language-specific context word. */
    val priorMass: Double = 1.0,
    /** Minimum language weight for a suggestion to be applied automatically. */
    val autoCorrectConfidence: Double = 0.7,
    /** Maximum number of merged suggestions returned. */
    val maxResults: Int = 8
) {
    init {
        require(contextWords >= 0) { "contextWords must be >= 0" }
        require(primaryPrior in 0.0..1.0) { "primaryPrior must be in 0..1" }
        require(recencyDecay > 0.0 && recencyDecay <= 1.0) { "recencyDecay must be in (0, 1]" }
        require(priorMass > 0.0) { "priorMass must be > 0" }
        require(autoCorrectConfidence in 0.0..1.0) { "autoCorrectConfidence must be in 0..1" }
        require(maxResults > 0) { "maxResults must be > 0" }
    }
}

/**
 * Estimates how likely the user is writing in each language, from the last words typed.
 *
 * Each context word that is valid in some but not all languages is evidence for the languages that accept it
 * (split evenly among them), weighted by recency. The result is a Bayesian-style shrinkage of that evidence
 * towards a prior that favours the primary language:
 *
 * `weight(l) = (priorMass * prior(l) + evidence(l)) / (priorMass + totalEvidence)`
 *
 * so weights always sum to 1, an empty or ambiguous context yields exactly the prior, and a single word is
 * never enough to overturn a strong prior.
 */
internal object LanguageConfidence {
    /**
     * @param languages all candidate languages (primary included).
     * @param context words typed before the current one, oldest first.
     * @param isValid whether [word] is valid in [language].
     */
    fun weights(
        languages: List<String>,
        primary: String,
        context: List<String>,
        config: MixedConfig,
        isValid: (language: String, word: String) -> Boolean
    ): Map<String, Double> {
        val prior = priors(languages, primary, config)
        if (languages.size < 2) return prior
        val evidence = HashMap<String, Double>()
        var total = 0.0
        val recent = context.filter { it.isNotBlank() }.takeLast(config.contextWords).asReversed()
        recent.forEachIndexed { age, word ->
            val accepting = languages.filter { isValid(it, word) }
            if (accepting.isEmpty() || accepting.size == languages.size) return@forEachIndexed
            val share = config.recencyDecay.pow(age) / accepting.size
            accepting.forEach { evidence.merge(it, share, Double::plus) }
            total += config.recencyDecay.pow(age)
        }
        val denominator = config.priorMass + total
        return languages.associateWith {
            (
                config.priorMass * prior.getValue(it) +
                    (evidence[it] ?: 0.0)
                ) /
                denominator
        }
    }

    fun priors(languages: List<String>, primary: String, config: MixedConfig): Map<String, Double> {
        if (languages.size < 2) return languages.associateWith { 1.0 }
        val others = (1.0 - config.primaryPrior) / (languages.size - 1)
        return languages.associateWith { if (it == primary) config.primaryPrior else others }
    }
}
