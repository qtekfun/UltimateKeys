// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.gesture

/**
 * The words a gesture can produce, stored compactly (a few bytes per word beyond its characters, no
 * object per word) and indexed by the first and last key of their path, which is how the decoder prunes.
 *
 * Words come from the same word lists the suggestion engine is built from, so their frequencies are the
 * engine's unigram model: [frequency] is the 0..255 value of the list, 255 being the most common word.
 */
@Suppress("LongParameterList")
class GestureVocabulary private constructor(
    val languages: List<String>,
    private val chars: CharArray,
    private val charStart: IntArray,
    internal val keys: ByteArray,
    internal val keyStart: IntArray,
    private val frequencies: ByteArray,
    private val wordLanguages: ByteArray,
    private val bucketStart: IntArray,
    private val bucketWords: IntArray
) {
    val size: Int get() = frequencies.size

    fun word(id: Int): String = String(chars, charStart[id], charStart[id + 1] - charStart[id])

    /** 0..255, higher is more common. */
    fun frequency(id: Int): Int = frequencies[id].toInt() and BYTE_MASK

    /** Index into [languages]. */
    fun languageIndex(id: Int): Int = wordLanguages[id].toInt()

    /** First (inclusive) position of the words whose path starts at [first] and ends at [last]. */
    internal fun bucketBegin(first: Int, last: Int): Int = bucketStart[
        first * GestureAlphabet.size +
            last
    ]

    internal fun bucketEnd(first: Int, last: Int): Int = bucketStart[
        first * GestureAlphabet.size +
            last +
            1
    ]

    /** The word id at [position] of a bucket. */
    internal fun idAt(position: Int): Int = bucketWords[position]

    /** True when [word] (compared ignoring case) is in the list of [languageIndex]. */
    fun contains(languageIndex: Int, word: String): Boolean {
        val sequence = GestureAlphabet.sequenceOf(word) ?: return false
        val first = sequence.first().toInt()
        val last = sequence.last().toInt()
        for (position in bucketBegin(first, last) until bucketEnd(first, last)) {
            val id = bucketWords[position]
            if (wordLanguages[id].toInt() == languageIndex &&
                word(id).equals(word, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    /** Collects words, then freezes them into a vocabulary. */
    class Builder(private val languages: List<String>, private val minFrequency: Int = 1) {
        private val text = StringBuilder()
        private val charOffsets = ArrayList<Int>()
        private val keyBytes = java.io.ByteArrayOutputStream()
        private val keyOffsets = ArrayList<Int>()
        private val freq = java.io.ByteArrayOutputStream()
        private val langs = java.io.ByteArrayOutputStream()
        private var count = 0

        init {
            require(languages.isNotEmpty() && languages.size <= MAX_LANGUAGES) {
                "1 to $MAX_LANGUAGES languages"
            }
        }

        /** Adds [word] of the language at [languageIndex]. Words that cannot be traced are ignored. */
        fun add(languageIndex: Int, word: String, frequency: Int): Builder {
            require(languageIndex in languages.indices) { "Unknown language $languageIndex" }
            if (frequency < minFrequency || frequency > MAX_FREQUENCY) return this
            val sequence = GestureAlphabet.sequenceOf(word) ?: return this
            charOffsets += text.length
            keyOffsets += keyBytes.size()
            text.append(word)
            keyBytes.write(sequence)
            freq.write(frequency)
            langs.write(languageIndex)
            count++
            return this
        }

        /** Word ids from the most to the least frequent (counting sort; ties keep list order). */
        private fun byFrequency(frequencies: ByteArray): IntArray {
            val start = IntArray(MAX_FREQUENCY + 2)
            frequencies.forEach { start[MAX_FREQUENCY - (it.toInt() and BYTE_MASK) + 1]++ }
            for (i in 0..MAX_FREQUENCY) start[i + 1] += start[i]
            val order = IntArray(frequencies.size)
            frequencies.forEachIndexed { id, f ->
                order[
                    start[
                        MAX_FREQUENCY -
                            (f.toInt() and BYTE_MASK)
                    ]++
                ] =
                    id
            }
            return order
        }

        fun build(): GestureVocabulary {
            val charStart = (charOffsets + text.length).toIntArray()
            val keyBytesArray = keyBytes.toByteArray()
            val keyStart = (keyOffsets + keyBytesArray.size).toIntArray()
            val buckets = GestureAlphabet.size * GestureAlphabet.size
            val bucketStart = IntArray(buckets + 1)
            val bucketOf = IntArray(count) { id ->
                keyBytesArray[keyStart[id]] * GestureAlphabet.size +
                    keyBytesArray[keyStart[id + 1] - 1]
            }
            bucketOf.forEach { bucketStart[it + 1]++ }
            for (i in 0 until buckets) bucketStart[i + 1] += bucketStart[i]
            val fill = bucketStart.copyOf(buckets)
            val bucketWords = IntArray(count)
            // Most frequent words first inside every bucket: the decoder meets its best candidates early.
            for (id in byFrequency(freq.toByteArray())) bucketWords[fill[bucketOf[id]]++] = id
            return GestureVocabulary(
                languages,
                text.toString().toCharArray(),
                charStart,
                keyBytesArray,
                keyStart,
                freq.toByteArray(),
                langs.toByteArray(),
                bucketStart,
                bucketWords
            )
        }
    }

    companion object {
        private const val BYTE_MASK = 0xFF
        private const val MAX_FREQUENCY = 255
        private const val MAX_LANGUAGES = 8
    }
}
