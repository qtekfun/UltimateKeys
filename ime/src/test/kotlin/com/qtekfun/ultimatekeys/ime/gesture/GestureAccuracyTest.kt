// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.gesture

import com.qtekfun.ultimatekeys.gesture.GestureContext
import com.qtekfun.ultimatekeys.gesture.GestureDecoder
import com.qtekfun.ultimatekeys.gesture.GestureVocabulary
import com.qtekfun.ultimatekeys.gesture.harness.Accuracy
import com.qtekfun.ultimatekeys.gesture.harness.EvaluationReport
import com.qtekfun.ultimatekeys.gesture.harness.GestureEvaluation
import com.qtekfun.ultimatekeys.gesture.harness.NoiseProfile
import java.io.File
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Offline accuracy of the decoder on synthetic gestures over the real layouts and word lists (task 10.6).
 * The regular run checks a sample; `-Pgesture.full=true` runs the 5,000 most common words of each language
 * and writes the tables of docs/gesture/RESULTS.md to `ime/build/gesture-results.md`.
 */
class GestureAccuracyTest {
    private class Language(val locale: Locale, val layout: String, val context: List<String>)

    private val languages = listOf(
        Language(GestureRig.spanish, "es_qwerty", listOf("también", "estaba")),
        Language(GestureRig.english, "en_qwerty", listOf("the", "would"))
    )

    private fun common(locale: Locale, every: Int = 1): List<String> =
        GestureRig.words(locale).take(COMMON).map {
            it.word
        }.filterIndexed { i, _ -> i % every == 0 }

    @Test
    fun `typical gestures reach the target on a sample of the common words`() {
        val decoder = GestureDecoder(GestureRig.vocabulary)
        for (language in languages) {
            val keyboard = GestureRig.keyboard(language.layout)
            val words = common(language.locale, every = SAMPLE_EVERY)
            val typical = GestureEvaluation(decoder, keyboard).run(words, NoiseProfile.Typical)
            val careful = GestureEvaluation(decoder, keyboard).run(words, NoiseProfile.Careful)
            assertTrue(typical.overall.top3Rate >= TARGET, "${language.locale} typical: $typical")
            assertTrue(careful.overall.top3Rate >= careful.overall.top1Rate)
            assertTrue(
                careful.overall.top3Rate >= TARGET_CAREFUL,
                "${language.locale} careful: $careful"
            )
            assertTrue(
                typical.overall.top1Rate >= TARGET_TOP1,
                "${language.locale} top-1: $typical"
            )
        }
    }

    @Test
    fun `full evaluation of the 5000 most common words`() {
        if (System.getProperty("gesture.full") != "true") return
        val out = StringBuilder()
        val mixed = GestureDecoder(GestureRig.vocabulary)
        out.append(HEADER_PROFILE)
        languages.forEach { profileRows(out, it, mixed) }
        out.append("\n").append(HEADER_SCENARIO)
        languages.forEach { scenarioRows(out, it, mixed) }
        out.append("\n### Typical profile by word length\n\n").append(HEADER_LENGTH)
        languages.forEach { lengthRows(out, it, mixed) }
        File(System.getProperty("user.dir"), "build/gesture-results.md").writeText(out.toString())
    }

    private fun profileRows(out: StringBuilder, language: Language, mixed: GestureDecoder) {
        val keyboard = GestureRig.keyboard(language.layout)
        val words = common(language.locale)
        for (profile in NoiseProfile.all) {
            val report = GestureEvaluation(mixed, keyboard).run(words, profile)
            row(out, language, profile.name, report)
        }
    }

    private fun scenarioRows(out: StringBuilder, language: Language, mixed: GestureDecoder) {
        val keyboard = GestureRig.keyboard(language.layout)
        val words = common(language.locale)
        val typical = NoiseProfile.Typical
        val context = GestureContext(previousWords = language.context)
        row(
            out,
            language,
            "mixed vocabulary, ${language.context} before",
            GestureEvaluation(mixed, keyboard).run(words, typical, context = context)
        )
        row(
            out,
            language,
            "own language only",
            GestureEvaluation(singleLanguage(language), keyboard).run(words, typical)
        )
    }

    private fun lengthRows(out: StringBuilder, language: Language, mixed: GestureDecoder) {
        val keyboard = GestureRig.keyboard(language.layout)
        val report = GestureEvaluation(
            mixed,
            keyboard
        ).run(common(language.locale), NoiseProfile.Typical)
        val code = language.locale.language
        report.byLength.forEach { (bucket, a) ->
            val rates = "${pct(a.top1Rate)} | ${pct(a.top3Rate)}"
            out.append("| $code | $bucket | ${a.total} | $rates |\n")
        }
        out.append("\nMisses (typical, $code): ${report.misses.take(MISSES)}\n\n")
    }

    private fun singleLanguage(language: Language): GestureDecoder {
        val builder = GestureVocabulary.Builder(
            listOf(language.locale.language),
            GestureSupport.MIN_FREQUENCY
        )
        GestureRig.words(language.locale).forEach { builder.add(0, it.word, it.frequency) }
        return GestureDecoder(builder.build())
    }

    private fun row(out: StringBuilder, language: Language, label: String, r: EvaluationReport) {
        val a: Accuracy = r.overall
        val rates = "${pct(a.top1Rate)} | ${pct(a.top3Rate)} | ${pct(a.top5Rate)}"
        out.append("| ${language.locale.language} | $label | ${a.total} | $rates |\n")
    }

    private fun pct(rate: Double) = String.format(Locale.ROOT, "%.1f%%", rate * 100)

    private companion object {
        const val COMMON = 5000
        const val SAMPLE_EVERY = 10
        const val TARGET = 0.90
        const val TARGET_CAREFUL = 0.95
        const val TARGET_TOP1 = 0.70
        const val MISSES = 25
        const val HEADER_PROFILE =
            "| Language | Profile | Words | Top-1 | Top-3 | Top-5 |\n|---|---|---|---|---|---|\n"
        const val HEADER_SCENARIO =
            "| Language | Scenario | Words | Top-1 | Top-3 | Top-5 |\n|---|---|---|---|---|---|\n"
        const val HEADER_LENGTH =
            "| Language | Letters | Words | Top-1 | Top-3 |\n|---|---|---|---|---|\n"
    }
}
