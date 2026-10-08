// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import groovy.json.JsonSlurper

/**
 * Turns the Unicode emoji list (`emoji-test.txt`) and the CLDR annotations into the compact assets
 * the emoji panel reads. Pure functions, unit-tested in build-logic; the Gradle task only fetches
 * and writes. Output formats are described in `docs/adr/0010-emoji-data.md`.
 */
object EmojiDataProcessor {
    /** One keyboard emoji and the skin-tone forms of it. */
    data class Emoji(val emoji: String, val name: String, val variants: List<String>)

    data class Group(val id: String, val emojis: List<Emoji>)

    /** A CLDR annotation: the spoken name and the search keywords. */
    data class Annotation(val name: String, val keywords: List<String>)

    private const val VARIATION_SELECTOR = "️"
    private const val TONE = "(?:light|medium-light|medium|medium-dark|dark) skin tone"
    private val TONE_PHRASE = Regex(TONE)
    private val TONE_BEFORE_MORE = Regex(": $TONE, ")
    private val TONE_AFTER_COMMA = Regex(", $TONE")
    private val TONE_AFTER_COLON = Regex(": $TONE")

    private val GROUP_IDS = mapOf(
        "Smileys & Emotion" to "smileys",
        "People & Body" to "people",
        "Animals & Nature" to "animals",
        "Food & Drink" to "food",
        "Travel & Places" to "travel",
        "Activities" to "activities",
        "Objects" to "objects",
        "Symbols" to "symbols",
        "Flags" to "flags"
    )

    /**
     * Parses `emoji-test.txt`: keeps fully-qualified emoji of the nine keyboard groups, in the
     * file's order, and attaches single-tone skin variants to their base emoji. Combinations of
     * two different tones are left out (the panel offers one tone at a time).
     */
    fun parseEmojiTest(text: String): List<Group> {
        val groups = LinkedHashMap<String, MutableList<RawEntry>>()
        var current: MutableList<RawEntry>? = null
        for (line in text.lineSequence()) {
            if (line.startsWith("# group: ")) {
                val id = GROUP_IDS[line.removePrefix("# group: ").trim()]
                current = id?.let { groups.getOrPut(it) { mutableListOf() } }
            } else if (current != null && !line.startsWith("#") && line.contains(';')) {
                parseLine(line)?.let { current.add(it) }
            }
        }
        return groups.map { (id, raws) -> Group(id, attachVariants(raws)) }
    }

    private class RawEntry(val emoji: String, val name: String)

    private fun parseLine(line: String): RawEntry? {
        val (codes, rest) = line.split(';', limit = 2).map { it.trim() }
        val status = rest.substringBefore('#').trim()
        if (status != "fully-qualified") return null
        val comment = rest.substringAfter('#').trim()
        // "<emoji> E<version> <name>"
        val parts = comment.split(' ', limit = 3)
        if (parts.size < 3) return null
        val emoji = codes.split(' ').joinToString("") { String(Character.toChars(it.toInt(16))) }
        return RawEntry(emoji, parts[2])
    }

    private fun attachVariants(raws: List<RawEntry>): List<Emoji> {
        val variants = HashMap<String, MutableList<String>>()
        val byName = raws.filterNot { isToned(it) }.associateBy { it.name }
        for (raw in raws) {
            val base = toneBaseName(raw) ?: continue
            if (base in byName) variants.getOrPut(base) { mutableListOf() }.add(raw.emoji)
        }
        return raws.filterNot { isToned(it) }.map { Emoji(it.emoji, it.name, variants[it.name].orEmpty()) }
    }

    private fun isToned(raw: RawEntry) = raw.name.contains("skin tone")

    /**
     * The name of the base emoji when [raw] is a single-tone variant (exactly one tone phrase in its
     * name), else null. "man: light skin tone, red hair" is a variant of "man: red hair".
     */
    private fun toneBaseName(raw: RawEntry): String? {
        if (TONE_PHRASE.findAll(raw.name).count() != 1) return null
        val name = raw.name
        return when {
            TONE_BEFORE_MORE.containsMatchIn(name) -> name.replaceFirst(TONE_BEFORE_MORE, ": ")
            TONE_AFTER_COMMA.containsMatchIn(name) -> name.replaceFirst(TONE_AFTER_COMMA, "")
            else -> name.replaceFirst(TONE_AFTER_COLON, "")
        }
    }

    /** `cldr-annotations-full/.../annotations.json` to a map keyed by the emoji without FE0F. */
    fun parseAnnotations(json: String): Map<String, Annotation> {
        val root = JsonSlurper().parseText(json) as Map<*, *>
        // The base file's root is "annotations", the derived file's (flags, hair, ...) "annotationsDerived".
        val outer = (root["annotations"] ?: root["annotationsDerived"]) as Map<*, *>
        val entries = outer["annotations"] as Map<*, *>
        val result = LinkedHashMap<String, Annotation>()
        for ((key, value) in entries) {
            val body = value as Map<*, *>
            val tts = (body["tts"] as? List<*>)?.firstOrNull()?.toString().orEmpty()
            val keywords = (body["default"] as? List<*>).orEmpty().map { it.toString() }
            result[stripSelector(key.toString())] = Annotation(tts, keywords)
        }
        return result
    }

    private fun stripSelector(emoji: String) = emoji.replace(VARIATION_SELECTOR, "")

    /** The panel catalogue: `@group` headers, then one base emoji per line followed by its variants. */
    fun renderCatalog(groups: List<Group>): String = buildString {
        append("# Generated by :emoji:generateEmojiData from emoji-test.txt. Do not edit.\n")
        for (group in groups) {
            append('@').append(group.id).append('\n')
            for (emoji in group.emojis) {
                append(emoji.emoji)
                emoji.variants.forEach { append(' ').append(it) }
                append('\n')
            }
        }
    }

    /**
     * One line per emoji that has an annotation: `emoji TAB name TAB keyword|keyword`. Keywords
     * equal to the name are dropped; the order is the catalogue's, which breaks ranking ties.
     */
    fun renderSearch(groups: List<Group>, annotations: Map<String, Annotation>): String = buildString {
        append("# Generated by :emoji:generateEmojiData from CLDR annotations. Do not edit.\n")
        for (emoji in groups.flatMap { it.emojis }) {
            val annotation = annotations[stripSelector(emoji.emoji)] ?: continue
            val name = clean(annotation.name)
            if (name.isEmpty()) continue
            val keywords = annotation.keywords.map(::clean).filter { it.isNotEmpty() && it != name }.distinct()
            append(emoji.emoji).append('\t').append(name).append('\t')
            append(keywords.joinToString("|")).append('\n')
        }
    }

    private fun clean(value: String) = value.replace('\t', ' ').replace('|', ' ').replace('\n', ' ').trim()
}
