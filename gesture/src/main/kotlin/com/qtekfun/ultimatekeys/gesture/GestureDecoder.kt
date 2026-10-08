// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max

/** A word a gesture may mean. A lower [cost] is a better match. */
data class GestureCandidate(val word: String, val cost: Float, val language: String)

/** What the decoder may know besides the finger's path. */
data class GestureContext(
    /** The words written before the gesture, oldest first. They tell which language is being written. */
    val previousWords: List<String> = emptyList(),
    /** Words the engine predicts to come next, lowercase, each with a confidence in 0..1. */
    val nextWords: Map<String, Float> = emptyMap(),
    /** The person's own dictionary: words that are not in the shipped lists. */
    val userWords: List<String> = emptyList()
)

/**
 * Turns the path of a finger into the words it most likely traced (word-gesture decoding).
 *
 * This is geometric pattern matching in the style of SHARK2 (Kristensson and Zhai, 2004; see
 * docs/gesture/DESIGN.md): every word has an ideal path through the centres of its keys, the gesture is
 * compared with those paths through a *shape* channel (size- and position-free) and a *location* channel
 * (where on the keyboard the finger went, with a tolerance of about one key), and the result is combined
 * with a language model: the unigram frequency of the word, the language the person is writing in and the
 * words the engine expects next.
 *
 * Stages: (1) prune by the keys near the first and last point and by path length; (2) score what is left
 * on [GestureConfig.resamplePoints] points per path, keeping a shortlist; (3) re-rank the shortlist with
 * the context and the person's own words. Stateless: safe to call from several threads.
 */
class GestureDecoder(
    private val vocabulary: GestureVocabulary,
    private val config: GestureConfig = GestureConfig(),
    private val primaryLanguage: Int = 0
) {
    private val prior = LanguagePrior(vocabulary, primaryLanguage)

    /**
     * @param path the finger's path, interleaved `[x0, y0, x1, y1, ...]` in the pixels of [keyboard].
     * @return up to [GestureConfig.maxCandidates] candidates, best first; empty when the gesture is too
     *   short, starts or ends away from every key, or matches nothing.
     */
    fun decode(
        path: FloatArray,
        keyboard: GestureKeyboard,
        context: GestureContext = GestureContext()
    ): List<GestureCandidate> {
        val count = PathMath.pointCount(path.size)
        if (count < 2 || keyboard.isEmpty) return emptyList()
        val length = PathMath.length(path, count)
        if (length < config.minPathUnits * keyboard.unit) return emptyList()
        val languageCosts = languageCosts(context)
        var radius = config.endpointRadius
        var found = emptyList<GestureCandidate>()
        // A finger that landed far from its key finds nothing at first: look again with a wider net.
        repeat(ATTEMPTS) {
            if (found.isEmpty()) {
                val run = Run(path, count, length, keyboard, languageCosts, radius * keyboard.unit)
                run.scanVocabulary()
                run.scanUserWords(context.userWords)
                found = run.finish(context)
                radius *= RELAX
            }
        }
        return found
    }

    /** Extra cost per language: 0 for the most likely language, growing with how unlikely the others are. */
    private fun languageCosts(context: GestureContext): FloatArray {
        val weights = prior.weights(context.previousWords)
        val best = weights.max()
        return FloatArray(weights.size) {
            (ln(best / weights[it]) * config.languageWeight).toFloat()
        }
    }

    /** The state of one decoding: the gesture's two normalisations, scratch buffers and the shortlist. */
    private inner class Run(
        private val path: FloatArray,
        private val count: Int,
        private val length: Float,
        private val keyboard: GestureKeyboard,
        private val languageCost: FloatArray,
        private val endpointRadius: Float
    ) {
        private val n = config.resamplePoints
        private val unit = keyboard.unit
        private val gesture = FloatArray(2 * n)
        private val normalized = FloatArray(2 * n)
        private val template = FloatArray(2 * n)
        private var polyline = FloatArray(2 * INITIAL_KEYS)
        private val ids = IntArray(config.shortlist)
        private val costs = FloatArray(config.shortlist)
        private var size = 0
        private val userWordList = ArrayList<String>()

        init {
            PathMath.resample(path, count, n, gesture)
            normalize(gesture, normalized)
        }

        fun scanVocabulary() {
            val startKeys = keyboard.keysNear(path[0], path[1], endpointRadius)
            val endKeys = keyboard.keysNear(
                path[2 * count - 2],
                path[2 * count - 1],
                endpointRadius
            )
            for (first in startKeys) {
                for (last in endKeys) {
                    for (position in vocabulary.bucketBegin(first, last) until
                        vocabulary.bucketEnd(first, last)) {
                        consider(vocabulary.idAt(position))
                    }
                }
            }
        }

        private fun consider(id: Int) {
            val languageAndFrequency =
                languageCost[vocabulary.languageIndex(id)] + frequencyCost(vocabulary.frequency(id))
            if (size == ids.size && languageAndFrequency >= costs[size - 1]) return
            val from = vocabulary.keyStart[id]
            val idealLength = keyboard.pathLength(
                vocabulary.keys,
                from,
                vocabulary.keyStart[id + 1]
            )
            if (!lengthFits(idealLength)) return
            val cost =
                languageAndFrequency + matchCost(vocabulary.keys, from, vocabulary.keyStart[id + 1])
            offer(id, cost)
        }

        fun scanUserWords(words: List<String>) {
            for (word in words) {
                val sequence = GestureAlphabet.sequenceOf(word)
                if (sequence != null && keyboard.endsNear(sequence, path, count, endpointRadius) &&
                    lengthFits(keyboard.pathLength(sequence, 0, sequence.size))
                ) {
                    val cost = frequencyCost(config.userWordFrequency) +
                        matchCost(sequence, 0, sequence.size)
                    userWordList += word
                    offer(-userWordList.size, cost)
                }
            }
        }

        private fun lengthFits(idealLength: Float): Boolean {
            if (idealLength.isNaN() || idealLength <= 0f) return false
            val ratio = length / idealLength
            return ratio >= config.minLengthRatio && ratio <= config.maxLengthRatio
        }

        private fun frequencyCost(frequency: Int): Float =
            config.frequencyWeight * (MAX_FREQUENCY - frequency) / MAX_FREQUENCY

        /** The cost of the two geometric channels for the word whose keys are `keys[from until to]`. */
        private fun matchCost(keys: ByteArray, from: Int, to: Int): Float {
            val points = to - from
            if (polyline.size < 2 * points) polyline = FloatArray(2 * points)
            for (i in 0 until points) {
                val key = keys[from + i].toInt()
                polyline[2 * i] = keyboard.centerX[key]
                polyline[2 * i + 1] = keyboard.centerY[key]
            }
            PathMath.resample(polyline, points, n, template)
            val location = locationDistance()
            val shape = shapeDistance()
            val s = shape / config.shapeSigma
            val l = location / config.locationSigma
            return if (config.channelPower == 2f) HALF * (s * s + l * l) else s + l
        }

        /** Mean distance, in keys, by which the finger left the tunnel around the template. */
        private fun locationDistance(): Float {
            var sum = 0f
            for (i in 0 until n) {
                val d =
                    hypot(
                        gesture[2 * i] - template[2 * i],
                        gesture[2 * i + 1] - template[2 * i + 1]
                    ) /
                        unit
                sum += max(0f, d - config.locationTolerance)
            }
            return sum / n
        }

        /** Mean distance between the two paths once both are moved to the origin and scaled to one size. */
        private fun shapeDistance(): Float {
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var sumX = 0f
            var sumY = 0f
            for (i in 0 until n) {
                val x = template[2 * i]
                val y = template[2 * i + 1]
                sumX += x
                sumY += y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            val scale = max(max(maxX - minX, maxY - minY), unit)
            val cx = sumX / n
            val cy = sumY / n
            var sum = 0f
            for (i in 0 until n) {
                sum += hypot(
                    normalized[2 * i] - (template[2 * i] - cx) / scale,
                    normalized[2 * i + 1] - (template[2 * i + 1] - cy) / scale
                )
            }
            return sum / n
        }

        /** Moves [source] to its centroid and divides by the larger side of its bounding box. */
        private fun normalize(source: FloatArray, target: FloatArray) {
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var sumX = 0f
            var sumY = 0f
            for (i in 0 until n) {
                val x = source[2 * i]
                val y = source[2 * i + 1]
                sumX += x
                sumY += y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            val scale = max(max(maxX - minX, maxY - minY), unit)
            for (i in 0 until n) {
                target[2 * i] = (source[2 * i] - sumX / n) / scale
                target[2 * i + 1] = (source[2 * i + 1] - sumY / n) / scale
            }
        }

        /** Keeps the shortlist sorted by cost, cheapest first. */
        private fun offer(id: Int, cost: Float) {
            if (size == ids.size && cost >= costs[size - 1]) return
            var at = if (size < ids.size) size else size - 1
            while (at > 0 && costs[at - 1] > cost) {
                ids[at] = ids[at - 1]
                costs[at] = costs[at - 1]
                at--
            }
            ids[at] = id
            costs[at] = cost
            if (size < ids.size) size++
        }

        fun finish(context: GestureContext): List<GestureCandidate> {
            val scored = ArrayList<GestureCandidate>(size)
            for (i in 0 until size) {
                val id = ids[i]
                val word = if (id >= 0) vocabulary.word(id) else userWordList[-id - 1]
                val language = if (id >=
                    0
                ) {
                    vocabulary.languages[vocabulary.languageIndex(id)]
                } else {
                    vocabulary.languages[primaryLanguage]
                }
                val bonus = (context.nextWords[word.lowercase()] ?: 0f) * config.contextWeight
                scored += GestureCandidate(word, costs[i] - bonus, language)
            }
            val seen = HashSet<String>()
            return scored.sortedBy { it.cost }
                .filter { seen.add(it.word.lowercase()) }
                .take(config.maxCandidates)
        }
    }

    private companion object {
        const val INITIAL_KEYS = 16
        const val MAX_FREQUENCY = 255f
        const val HALF = 0.5f
        const val ATTEMPTS = 2
        const val RELAX = 1.7f
    }
}
