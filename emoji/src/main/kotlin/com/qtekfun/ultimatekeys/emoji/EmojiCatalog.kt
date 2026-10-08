// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.emoji

/** The panel's categories, in display order. [id] matches the `@group` headers of `catalog.txt`. */
enum class EmojiCategory(val id: String, val icon: String) {
    SMILEYS("smileys", "😀"),
    PEOPLE("people", "👋"),
    ANIMALS("animals", "🐻"),
    FOOD("food", "🍔"),
    TRAVEL("travel", "🚗"),
    ACTIVITIES("activities", "⚽"),
    OBJECTS("objects", "💡"),
    SYMBOLS("symbols", "❤️"),
    FLAGS("flags", "🏁");

    companion object {
        fun fromId(id: String): EmojiCategory? = entries.firstOrNull { it.id == id }
    }
}

/** The five Fitzpatrick skin tones; [NONE] is the default yellow form. */
@Suppress("MagicNumber")
enum class SkinTone(val modifier: Int?) {
    NONE(null),
    LIGHT(0x1F3FB),
    MEDIUM_LIGHT(0x1F3FC),
    MEDIUM(0x1F3FD),
    MEDIUM_DARK(0x1F3FE),
    DARK(0x1F3FF);

    companion object {
        /** Unknown or out-of-range stored values fall back to [NONE]. */
        fun fromOrdinal(ordinal: Int): SkinTone = entries.getOrElse(ordinal) { NONE }
    }
}

/** One emoji of the panel with its skin-tone forms, in tone order when it has them. */
data class EmojiEntry(val emoji: String, val variants: List<String> = emptyList()) {
    /** The form for [tone], or the plain emoji when it has no such form. */
    fun forTone(tone: SkinTone): String {
        val modifier = tone.modifier ?: return emoji
        return variants.firstOrNull { v -> v.codePoints().anyMatch { it == modifier } } ?: emoji
    }

    val hasTones: Boolean get() = variants.isNotEmpty()
}

data class EmojiGroup(val category: EmojiCategory, val entries: List<EmojiEntry>)

/** The emoji of the panel by category. Built from the generated `catalog.txt`. */
class EmojiCatalog(val groups: List<EmojiGroup>) {
    private val byEmoji: Map<String, EmojiEntry> by lazy {
        groups.flatMap { it.entries }.associateBy { it.emoji }
    }

    fun group(category: EmojiCategory): List<EmojiEntry> =
        groups.firstOrNull { it.category == category }?.entries.orEmpty()

    /** The entry for a base emoji, or for any of its toned forms. */
    fun entryOf(emoji: String): EmojiEntry? = byEmoji[emoji]
        ?: groups.asSequence().flatMap { it.entries }.firstOrNull { emoji in it.variants }

    /** Drops emoji (and variants) the device cannot draw: newer than its emoji font. */
    fun filtered(canDraw: (String) -> Boolean): EmojiCatalog = EmojiCatalog(
        groups.map { group ->
            EmojiGroup(
                group.category,
                group.entries.filter { canDraw(it.emoji) }
                    .map { it.copy(variants = it.variants.filter(canDraw)) }
            )
        }.filter { it.entries.isNotEmpty() }
    )

    companion object {
        /** Parses `catalog.txt`: `@id` starts a group, then one entry per line (emoji then variants). */
        fun parse(text: String): EmojiCatalog {
            val groups = ArrayList<EmojiGroup>()
            var category: EmojiCategory? = null
            var entries = ArrayList<EmojiEntry>()

            fun flush() {
                category?.let { groups.add(EmojiGroup(it, entries)) }
                entries = ArrayList()
            }
            for (line in text.lineSequence()) {
                when {
                    line.isBlank() || line.startsWith("#") -> Unit

                    line.startsWith("@") -> {
                        flush()
                        category = EmojiCategory.fromId(line.substring(1).trim())
                    }

                    else -> {
                        val parts = line.trim().split(' ')
                        entries.add(EmojiEntry(parts.first(), parts.drop(1)))
                    }
                }
            }
            flush()
            return EmojiCatalog(groups)
        }
    }
}
