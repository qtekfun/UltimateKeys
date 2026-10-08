// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

import com.qtekfun.ultimatekeys.gesture.harness.GestureEvaluation
import com.qtekfun.ultimatekeys.gesture.harness.NoiseProfile
import com.qtekfun.ultimatekeys.gesture.harness.SyntheticGestureGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GestureVocabularyTest {
    private val vocabulary = TestKeyboard.vocabulary(
        Triple(0, "hola", 200),
        Triple(1, "hello", 180),
        Triple(1, "a", 255),
        Triple(1, "e-mail", 100),
        Triple(0, "rare", 0),
        Triple(0, "año", 90)
    )

    @Test
    fun `only words that can be traced are kept`() {
        assertEquals(3, vocabulary.size)
    }

    @Test
    fun `words, frequencies and languages come back as stored`() {
        val words = (0 until vocabulary.size).associate { vocabulary.word(it) to it }
        val hola = words.getValue("hola")
        assertEquals(200, vocabulary.frequency(hola))
        assertEquals(0, vocabulary.languageIndex(hola))
        assertEquals(1, vocabulary.languageIndex(words.getValue("hello")))
    }

    @Test
    fun `membership ignores case and is per language`() {
        assertTrue(vocabulary.contains(0, "Hola"))
        assertFalse(vocabulary.contains(1, "hola"))
        assertTrue(vocabulary.contains(1, "HELLO"))
        assertFalse(vocabulary.contains(0, "adios"))
        assertFalse(vocabulary.contains(0, "a"))
    }

    @Test
    fun `the minimum frequency filters the list`() {
        val strict = GestureVocabulary.Builder(listOf("es"), minFrequency = 100)
            .add(0, "hola", 200).add(0, "casa", 50).build()
        assertEquals(1, strict.size)
    }

    @Test
    fun `a bad language index is refused`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            GestureVocabulary.Builder(listOf("es")).add(3, "hola", 10)
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            GestureVocabulary.Builder(emptyList())
        }
    }
}

class LanguagePriorTest {
    private val vocabulary = TestKeyboard.vocabulary(
        Triple(0, "hola", 200),
        Triple(0, "casa", 200),
        Triple(1, "hello", 200),
        Triple(1, "house", 200),
        Triple(0, "lunch", 100),
        Triple(1, "lunch", 100)
    )
    private val prior = LanguagePrior(vocabulary)

    @Test
    fun `without context the primary language is favoured`() {
        val w = prior.weights(emptyList())
        assertEquals(0.75, w[0], 1e-9)
        assertEquals(0.25, w[1], 1e-9)
    }

    @Test
    fun `foreign words shift the weights towards their language, shared words say nothing`() {
        val english = prior.weights(listOf("hello", "house"))
        assertTrue(english[1] > english[0])
        assertEquals(1.0, english.sum(), 1e-9)
        assertEquals(
            prior.weights(emptyList()).toList(),
            prior.weights(listOf("lunch", "zzzz")).toList()
        )
        val one = prior.weights(listOf("hello"))
        assertTrue(one[1] > prior.weights(emptyList())[1], "one foreign word already counts")
    }

    @Test
    fun `a single language has all the weight`() {
        val mono =
            LanguagePrior(TestKeyboard.vocabulary(Triple(0, "hola", 10), languages = listOf("es")))
        assertEquals(listOf(1.0), mono.weights(listOf("x")).toList())
    }
}

class GestureDecoderTest {
    private val keyboard = TestKeyboard.keyboard()
    private val vocabulary = TestKeyboard.vocabulary(
        Triple(0, "hola", 200),
        Triple(0, "hora", 150),
        Triple(0, "hoja", 120),
        Triple(0, "para", 210),
        Triple(0, "pasa", 150),
        Triple(1, "hello", 180),
        Triple(1, "help", 170),
        Triple(1, "world", 190),
        Triple(1, "word", 160)
    )
    private val decoder = GestureDecoder(vocabulary)

    private fun decode(word: String, context: GestureContext = GestureContext()) =
        decoder.decode(TestKeyboard.pathOf(word), keyboard, context).map { it.word }

    @Test
    fun `the exact path of a word decodes to that word`() {
        assertEquals("hola", decode("hola").first())
        assertEquals("hello", decode("hello").first())
        assertEquals("world", decode("world").first())
        assertEquals("word", decode("word").first())
    }

    @Test
    fun `look-alike words stay on the list`() {
        val ranked = decode("hola")
        assertTrue("hora" in ranked && "hoja" in ranked)
    }

    @Test
    fun `a more frequent word wins between near-equal shapes`() {
        // Aim between the r and the s of para / pasa, closer to the s.
        val p = TestKeyboard.center('p')
        val a = TestKeyboard.center('a')
        val r = TestKeyboard.center('r')
        val s = TestKeyboard.center('s')
        val mid = (r.first + s.first) / 2f to (r.second + s.second) / 2f
        val path =
            floatArrayOf(
                p.first,
                p.second,
                a.first,
                a.second,
                mid.first,
                mid.second,
                a.first,
                a.second
            )
        val words = decoder.decode(path, keyboard).map { it.word }
        assertEquals(listOf("para", "pasa"), words.take(2))
    }

    @Test
    fun `the language of the context moves a word up`() {
        val ambiguous = TestKeyboard.vocabulary(
            Triple(0, "hola", 150),
            Triple(0, "mundo", 150),
            Triple(1, "hold", 150),
            Triple(1, "world", 150),
            Triple(1, "wound", 150)
        )
        val d = GestureDecoder(ambiguous)
        val path = TestKeyboard.pathOf("hold")
        val spanish = d.decode(
            path,
            keyboard,
            GestureContext(previousWords = listOf("hola", "mundo"))
        )
        val english = d.decode(
            path,
            keyboard,
            GestureContext(previousWords = listOf("world", "wound"))
        )
        assertEquals("hold", english.first().word)
        assertEquals("en", english.first().language)
        assertTrue(english.first().cost < spanish.first { it.word == "hold" }.cost)
    }

    @Test
    fun `words the engine expects next and the person's own words are considered`() {
        val path = TestKeyboard.pathOf("hola")
        val plain = decoder.decode(path, keyboard).first { it.word == "hora" }
        val boosted = decoder.decode(
            path,
            keyboard,
            GestureContext(nextWords = mapOf("hora" to 1f))
        ).first { it.word == "hora" }
        assertEquals(GestureConfig().contextWeight, plain.cost - boosted.cost, 1e-3f)
        val own = decoder.decode(
            TestKeyboard.pathOf("hobo"),
            keyboard,
            GestureContext(userWords = listOf("hobo", "e-mail", "xx", "ñoño"))
        )
        assertEquals("hobo", own.first().word)
        assertEquals("es", own.first().language)
    }

    @Test
    fun `short, keyless and off-keyboard gestures give nothing`() {
        assertTrue(decoder.decode(floatArrayOf(10f, 10f), keyboard).isEmpty())
        assertTrue(
            decoder.decode(TestKeyboard.pathOf("hola"), GestureKeyboard(emptyList())).isEmpty()
        )
        val (x, y) = TestKeyboard.center('h')
        assertTrue(decoder.decode(floatArrayOf(x, y, x + 5f, y), keyboard).isEmpty())
        assertTrue(decoder.decode(TestKeyboard.pathOf("qp"), keyboard).isEmpty())
    }

    @Test
    fun `a finger that ended far from the last key is retried with a wider net`() {
        val path = TestKeyboard.pathOf("hola")
        path[path.size - 2] += 120f
        assertEquals("hola", decoder.decode(path, keyboard).first().word)
    }

    @Test
    fun `words with a letter that has no key are traced by the base letter`() {
        val v = TestKeyboard.vocabulary(Triple(0, "año", 200), Triple(0, "ano", 100))
        val d = GestureDecoder(v)
        assertEquals(
            listOf("año", "ano"),
            d.decode(TestKeyboard.pathOf("ano"), keyboard).map { it.word }
        )
    }

    @Test
    fun `words that need a key without any stand-in are never candidates`() {
        val v = TestKeyboard.vocabulary(Triple(0, "дом", 200), Triple(0, "ano", 100))
        val d = GestureDecoder(v)
        assertEquals(listOf("ano"), d.decode(TestKeyboard.pathOf("ano"), keyboard).map { it.word })
    }

    @Test
    fun `duplicates across languages are listed once`() {
        val v = TestKeyboard.vocabulary(Triple(0, "hola", 200), Triple(1, "hola", 100))
        val d = GestureDecoder(v)
        assertEquals(1, d.decode(TestKeyboard.pathOf("hola"), keyboard).size)
    }

    @Test
    fun `a gesture that is far too long or too short for a word does not match it`() {
        val tooShort = TestKeyboard.pathOf("ho")
        assertFalse("hola" in decoder.decode(tooShort, keyboard).map { it.word })
    }

    @Test
    fun `noisy paths still decode, and the synthetic generator is deterministic`() {
        val gen1 = SyntheticGestureGenerator(keyboard, NoiseProfile.Typical, seed = 5)
        val gen2 = SyntheticGestureGenerator(keyboard, NoiseProfile.Typical, seed = 5)
        val a = gen1.generate("world")!!
        assertTrue(a.contentEquals(gen2.generate("world")!!))
        assertTrue(a.size > 20, "sampled like a touch screen")
        assertEquals("world", decoder.decode(a, keyboard).first().word)
        assertNull(gen1.generate("a"))
        assertNull(
            SyntheticGestureGenerator(
                GestureKeyboard(emptyList()),
                NoiseProfile.Typical,
                1
            ).generate("hola")
        )
    }

    @Test
    fun `the evaluation counts hits and skips what cannot be traced`() {
        val eval = GestureEvaluation(decoder, keyboard)
        val report = eval.run(
            listOf("hola", "hello", "world", "para", "word", "zzz-not", "a"),
            NoiseProfile.Careful
        )
        assertEquals(2, report.skipped)
        assertEquals(5, report.overall.total)
        assertTrue(report.overall.top3Rate >= 0.99, report.toString())
        assertTrue(
            report.overall.top1 <= report.overall.top3 && report.overall.top3 <= report.overall.top5
        )
        assertEquals(5, report.byLength.values.sumOf { it.total })
        assertNotNull(NoiseProfile.all.single { it.name == "sloppy" })
        val unknown = eval.run(listOf("hell"), NoiseProfile.Sloppy, maxMisses = 1)
        assertEquals(1, unknown.overall.total)
    }

    @Test
    fun `accuracy arithmetic`() {
        val a = com.qtekfun.ultimatekeys.gesture.harness.Accuracy(4, 2, 3, 4)
        val sum = a + com.qtekfun.ultimatekeys.gesture.harness.Accuracy.Empty
        assertEquals(0.5, sum.top1Rate, 1e-9)
        assertEquals(0.75, sum.top3Rate, 1e-9)
        assertEquals(1.0, sum.top5Rate, 1e-9)
        assertEquals(0.0, com.qtekfun.ultimatekeys.gesture.harness.Accuracy.Empty.top1Rate)
    }
}
