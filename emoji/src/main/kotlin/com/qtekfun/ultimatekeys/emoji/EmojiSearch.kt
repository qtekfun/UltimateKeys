// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.emoji

import java.text.Normalizer
import java.util.Locale

/** Text as the search sees it: lower case, accents removed, split into words. */
object SearchText {
    private val MARKS = Regex("\\p{M}+")
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    /** "Corazón" and "corazon" normalize to the same string. */
    fun normalize(text: String): String =
        MARKS.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")

    fun words(text: String): List<String> =
        normalize(text).split(SEPARATORS).filter { it.isNotEmpty() }
}

/** One annotated emoji in one language. */
data class EmojiAnnotation(val emoji: String, val name: String, val keywords: List<String>)

/** A found emoji and why it ranked where it did. */
data class EmojiHit(val emoji: String, val name: String, val score: Int)

/**
 * Offline search over the CLDR names and keywords of one language. Matching is accent-insensitive
 * and by word prefix; every word of the query must match. Ranking prefers names over keywords and
 * whole words over prefixes; ties keep the panel's order.
 */
class EmojiSearchIndex(private val annotations: List<EmojiAnnotation>) {
    private class Posting(val token: String, val entry: Int, val score: Int)

    /** Postings sorted by token, so a prefix is one contiguous range found by binary search. */
    private val postings: List<Posting>

    init {
        val list = ArrayList<Posting>()
        annotations.forEachIndexed { index, annotation ->
            val nameWords = SearchText.words(annotation.name)
            nameWords.forEachIndexed { position, token ->
                list.add(Posting(token, index, if (position == 0) FIRST_NAME_WORD else NAME_WORD))
            }
            annotation.keywords.flatMap(SearchText::words).forEach {
                list.add(Posting(it, index, KEYWORD))
            }
        }
        list.sortBy { it.token }
        postings = list
    }

    val size: Int get() = annotations.size

    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<EmojiHit> {
        val words = SearchText.words(query)
        if (words.isEmpty()) return emptyList()
        var scores: Map<Int, Int>? = null
        for (word in words) {
            val matches = bestScores(word)
            scores = if (scores == null) {
                matches
            } else {
                scores.mapNotNull { (entry, total) ->
                    matches[entry]?.let { entry to total + it }
                }.toMap()
            }
            if (scores.isEmpty()) return emptyList()
        }
        val whole = SearchText.normalize(query).trim()
        return checkNotNull(scores).map { (entry, score) ->
            val name = SearchText.normalize(annotations[entry].name)
            val bonus = when {
                name == whole -> WHOLE_NAME
                name.startsWith(whole) -> NAME_STARTS
                else -> 0
            }
            Triple(entry, score + bonus, name.length)
        }.sortedWith(
            compareByDescending<Triple<Int, Int, Int>> {
                it.second
            }.thenBy { it.third }.thenBy { it.first }
        )
            .take(limit)
            .map { (entry, score, _) ->
                EmojiHit(annotations[entry].emoji, annotations[entry].name, score)
            }
    }

    /** The best score per entry for one query word, over all tokens that start with it. */
    private fun bestScores(word: String): Map<Int, Int> {
        var low = 0
        var high = postings.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (postings[mid].token < word) low = mid + 1 else high = mid
        }
        val best = HashMap<Int, Int>()
        var i = low
        while (i < postings.size && postings[i].token.startsWith(word)) {
            val posting = postings[i]
            val score = posting.score + if (posting.token == word) EXACT_WORD else 0
            val previous = best[posting.entry]
            if (previous == null || score > previous) best[posting.entry] = score
            i++
        }
        return best
    }

    companion object {
        const val DEFAULT_LIMIT = 60

        // Weights: a name word beats a keyword, and a whole word beats a prefix.
        private const val FIRST_NAME_WORD = 30
        private const val NAME_WORD = 24
        private const val KEYWORD = 10
        private const val EXACT_WORD = 20
        private const val NAME_STARTS = 15
        private const val WHOLE_NAME = 40

        /** Parses `search_<lang>.tsv`: `emoji TAB name TAB keyword|keyword`. */
        fun parse(text: String): EmojiSearchIndex {
            val annotations = text.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val parts = line.split('\t')
                    EmojiAnnotation(
                        emoji = parts[0],
                        name = parts.getOrElse(1) { "" },
                        keywords = parts.getOrElse(2) { "" }.split('|').filter { it.isNotEmpty() }
                    )
                }
                .toList()
            return EmojiSearchIndex(annotations)
        }
    }
}

/** Searches several languages at once (the keyboard is bilingual); an emoji keeps its best score. */
class EmojiSearch(private val indexes: List<EmojiSearchIndex>) {
    fun search(query: String, limit: Int = EmojiSearchIndex.DEFAULT_LIMIT): List<EmojiHit> {
        val best = LinkedHashMap<String, EmojiHit>()
        // Indexes are in preference order: on equal scores the earlier language names the emoji.
        for (hit in indexes.flatMap { it.search(query, limit) }) {
            val previous = best[hit.emoji]
            if (previous == null || hit.score > previous.score) best[hit.emoji] = hit
        }
        return best.values.sortedByDescending { it.score }.take(limit)
    }
}
