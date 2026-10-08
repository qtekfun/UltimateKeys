// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine.mixed

import com.qtekfun.ultimatekeys.engine.Suggestion
import com.qtekfun.ultimatekeys.engine.SuggestionEngine
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Lets people type Spanish and English (or any set of languages) without switching keyboards.
 *
 * Every configured engine is queried for each request. Candidate lists are then merged using a per-language
 * confidence weight inferred from the last few context words (see [LanguageConfidence]); [primary] wins when the
 * context says nothing. The `locale` parameter of the [SuggestionEngine] methods is a hint that is not used to
 * restrict the search: all configured languages always take part.
 *
 * Merging rules:
 * - Scores are only comparable inside one engine's list, so each list is normalised to 0..1 (best = 1) and
 *   multiplied by its language weight. Scores of the same word coming from several languages add up.
 * - Words are deduplicated case-insensitively; the spelling and locale of the strongest contributor are kept.
 * - Ties are broken by language weight, then primary language, then alphabetically, so output is deterministic.
 * - Autocorrect is only allowed on the top suggestion, when that suggestion's strongest language is at least
 *   [MixedConfig.autoCorrectConfidence] confident and its engine asked for it, and the word being typed is not
 *   already valid in any configured language (a valid word from the other language is never "corrected").
 *
 * With more than two languages the same rules apply: every language gets a weight, the primary one starts ahead
 * but each other language keeps at least [MixedConfig.minSecondaryPrior], and autocorrect is allowed when the top
 * suggestion's language clearly leads (at least twice the runner-up) even if its weight is below the gate, since
 * with six languages a weight of 0.7 is out of reach.
 *
 * @param engines one engine per language, keyed by locale; only the language part of the key is used. Regional
 *   variants of one language (en-US, en-GB) cannot be mixed: the keyboard enables only one of them.
 */
class MixedSuggestionEngine(
    engines: Map<Locale, SuggestionEngine>,
    private val primary: Locale = Locale.forLanguageTag("es"),
    private val config: MixedConfig = MixedConfig(),
    /**
     * False when the engines are shared with something that outlives this object (the keyboard swaps in a new
     * mixed engine whenever the enabled languages or the active language change, over the same native engine).
     */
    private val closeEngines: Boolean = true
) : SuggestionEngine {
    private class Member(val locale: Locale, val engine: SuggestionEngine)

    private val members: Map<String, Member> = engines.entries.associate { (locale, engine) ->
        locale.language to Member(locale, engine)
    }
    private val primaryLanguage = primary.language
    private val languages: List<String> = members.keys.sortedWith(
        compareBy({
            it != primaryLanguage
        }, { it })
    )

    init {
        require(members.isNotEmpty()) { "At least one engine is required" }
        require(members.size == engines.size) { "Engines must have distinct languages" }
        require(primaryLanguage in members) { "The primary locale $primary has no engine" }
    }

    /** Current language weights for [context], summing to 1. Exposed for diagnostics and tests. */
    fun languageWeights(context: List<String>): Map<String, Double> = LanguageConfidence.weights(
        languages,
        primaryLanguage,
        context,
        config
    ) { language, word ->
        members.getValue(language).let { it.engine.isValidWord(word, it.locale) }
    }

    override fun suggest(
        context: List<String>,
        composing: String,
        locale: Locale
    ): List<Suggestion> {
        val weights = languageWeights(context)
        val lists = languages.map { language ->
            val member = members.getValue(language)
            language to member.engine.suggest(context, composing, member.locale)
        }
        val merged = merge(lists, weights)
        if (merged.isEmpty()) return merged
        return gateAutoCorrect(merged, composing, weights)
    }

    override fun predictNext(context: List<String>, locale: Locale): List<Suggestion> {
        val weights = languageWeights(context)
        val lists = languages.map { language ->
            val member = members.getValue(language)
            language to member.engine.predictNext(context, member.locale)
        }
        return merge(lists, weights).map { it.copy(autoCorrect = false) }
    }

    /** A word is valid when any configured language accepts it. */
    override fun isValidWord(word: String, locale: Locale): Boolean =
        members.values.any { it.engine.isValidWord(word, it.locale) }

    /**
     * Learns [word] in the language it most plausibly belongs to: the only language that knows it, otherwise
     * the language the context points to. Learning it everywhere would pollute both histories.
     */
    override fun learn(word: String, context: List<String>, locale: Locale) {
        val knowing = languages.filter {
            members.getValue(it).let { m -> m.engine.isValidWord(word, m.locale) }
        }
        val target = knowing.singleOrNull() ?: languageWeights(context).maxWith(
            compareBy<Map.Entry<String, Double>> {
                it.value
            }.thenBy { it.key == primaryLanguage }.thenBy { it.key }
        ).key
        members.getValue(target).let { it.engine.learn(word, context, it.locale) }
    }

    /** Adds to the engine of [locale]'s language, or to the primary one if that language is not configured. */
    override fun addToUserDictionary(word: String, locale: Locale) {
        memberFor(locale).let { it.engine.addToUserDictionary(word, it.locale) }
    }

    /** Removes [word] from every language, since it may have been added to any of them. */
    override fun removeFromUserDictionary(word: String, locale: Locale) {
        members.values.forEach { it.engine.removeFromUserDictionary(word, it.locale) }
    }

    /** The words of [locale]'s language, or those of all languages (deduplicated, sorted) if it is not configured. */
    override fun userDictionaryWords(locale: Locale): List<String> {
        members[locale.language]?.let { return it.engine.userDictionaryWords(it.locale) }
        return members.values.flatMap {
            it.engine.userDictionaryWords(it.locale)
        }.distinct().sorted()
    }

    override fun clearLearned() {
        members.values.forEach { it.engine.clearLearned() }
    }

    /** Closes every engine even if some fail; the first failure is rethrown with the others suppressed. */
    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        if (!closeEngines) return
        var failure: Throwable? = null
        for (member in members.values) {
            try {
                member.engine.close()
            } catch (e: Exception) {
                failure?.addSuppressed(e) ?: run { failure = e }
            }
        }
        failure?.let { throw it }
    }

    private fun memberFor(locale: Locale): Member =
        members[locale.language] ?: members.getValue(primaryLanguage)

    private class Candidate(
        var word: String,
        var locale: Locale?,
        var bestWeight: Double,
        var bestScore: Double
    ) {
        var total = 0.0
        var autoCorrect = false
    }

    private fun merge(
        lists: List<Pair<String, List<Suggestion>>>,
        weights: Map<String, Double>
    ): List<Suggestion> {
        val byKey = LinkedHashMap<String, Candidate>()
        for ((language, suggestions) in lists) {
            val weight = weights.getValue(language)
            val top = max(1, suggestions.maxOfOrNull { it.score } ?: 0)
            for (s in suggestions) {
                val score = max(0, s.score).toDouble() / top * weight
                val candidate = byKey.getOrPut(s.word.lowercase(Locale.ROOT)) {
                    Candidate(s.word, s.locale ?: members.getValue(language).locale, -1.0, -1.0)
                }
                candidate.total += score
                if (score > candidate.bestScore ||
                    (score == candidate.bestScore && weight > candidate.bestWeight)
                ) {
                    candidate.word = s.word
                    candidate.locale = s.locale ?: members.getValue(language).locale
                    candidate.bestScore = score
                    candidate.bestWeight = weight
                    candidate.autoCorrect = s.autoCorrect
                }
            }
        }
        return byKey.values.sortedWith(
            compareByDescending<Candidate> { it.total }
                .thenByDescending { it.bestWeight }
                .thenByDescending { it.locale?.language == primaryLanguage }
                .thenBy { it.word }
        ).take(config.maxResults).map {
            Suggestion(it.word, (it.total * SCORE_SCALE).roundToInt(), it.autoCorrect, it.locale)
        }
    }

    private fun gateAutoCorrect(
        merged: List<Suggestion>,
        composing: String,
        weights: Map<String, Double>
    ): List<Suggestion> {
        val top = merged.first()
        val language = top.locale?.language
        val allowed = top.autoCorrect &&
            composing.isNotEmpty() &&
            !top.word.equals(composing, ignoreCase = false) &&
            confident(language, weights) &&
            !isValidWord(composing, primary)
        return merged.mapIndexed { index, s ->
            if (index == 0 &&
                allowed
            ) {
                s
            } else {
                s.copy(autoCorrect = false)
            }
        }
    }

    private fun confident(language: String?, weights: Map<String, Double>): Boolean {
        val weight = weights[language] ?: return false
        if (weight >= config.autoCorrectConfidence) return true
        if (weights.size < MANY_LANGUAGES) return false
        val runnerUp = weights.filterKeys { it != language }.values.maxOrNull() ?: 0.0
        return weight >= LEAD_FACTOR * runnerUp
    }

    private companion object {
        const val SCORE_SCALE = 1000
        const val MANY_LANGUAGES = 3
        const val LEAD_FACTOR = 2.0
    }
}
