// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.emoji

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmojiCatalogTest {
    private val wave = "👋"
    private val tones = (0x1F3FB..0x1F3FF).map { wave + String(Character.toChars(it)) }

    private val sample = EmojiCatalog.parse(
        "# header\n@smileys\n😀\n@people\n${(
            listOf(
                wave
            ) + tones
            ).joinToString(" ")}\n@unknown\nX\n"
    )

    @Test
    fun `parse groups entries by category and skips unknown groups`() {
        assertEquals(
            listOf(EmojiCategory.SMILEYS, EmojiCategory.PEOPLE),
            sample.groups.map {
                it.category
            }
        )
        assertEquals(listOf("😀"), sample.group(EmojiCategory.SMILEYS).map { it.emoji })
        assertTrue(sample.group(EmojiCategory.FLAGS).isEmpty())
    }

    @Test
    fun `skin tones pick the matching variant`() {
        val entry = sample.group(EmojiCategory.PEOPLE).single()
        assertTrue(entry.hasTones)
        assertEquals(wave, entry.forTone(SkinTone.NONE))
        assertEquals(tones[0], entry.forTone(SkinTone.LIGHT))
        assertEquals(tones[2], entry.forTone(SkinTone.MEDIUM))
        assertEquals(tones[4], entry.forTone(SkinTone.DARK))
        assertEquals("😀", EmojiEntry("😀").forTone(SkinTone.DARK))
        assertFalse(EmojiEntry("😀").hasTones)
    }

    @Test
    fun `entryOf finds a base emoji or a toned form`() {
        assertEquals(wave, sample.entryOf(tones[3])?.emoji)
        assertNotNull(sample.entryOf(wave))
        assertNull(sample.entryOf("nope"))
    }

    @Test
    fun `filtered drops what the device cannot draw and empty groups`() {
        val drawable = setOf(wave, tones[0])
        val filtered = sample.filtered { it in drawable }
        assertEquals(listOf(EmojiCategory.PEOPLE), filtered.groups.map { it.category })
        assertEquals(listOf(tones[0]), filtered.group(EmojiCategory.PEOPLE).single().variants)
    }

    @Test
    fun `skin tone ordinals fall back to none`() {
        assertEquals(SkinTone.MEDIUM, SkinTone.fromOrdinal(3))
        assertEquals(SkinTone.NONE, SkinTone.fromOrdinal(99))
        assertEquals(SkinTone.NONE, SkinTone.fromOrdinal(-1))
        assertEquals(EmojiCategory.FOOD, EmojiCategory.fromId("food"))
        assertNull(EmojiCategory.fromId("nope"))
    }

    @Test
    fun `recents move to the front without duplicates and are capped`() {
        var stored = ""
        stored = EmojiRecents.add(stored, "a")
        stored = EmojiRecents.add(stored, "b")
        stored = EmojiRecents.add(stored, "a")
        assertEquals(listOf("a", "b"), EmojiRecents.parse(stored))
        repeat(EmojiRecents.MAX + 5) { stored = EmojiRecents.add(stored, "e$it") }
        assertEquals(EmojiRecents.MAX, EmojiRecents.parse(stored).size)
        assertEquals("e${EmojiRecents.MAX + 4}", EmojiRecents.parse(stored).first())
        assertEquals(stored, EmojiRecents.add(stored, " "))
        assertEquals(stored, EmojiRecents.add(stored, "a b"))
        assertEquals(emptyList<String>(), EmojiRecents.parse(""))
    }

    // Real data.

    private val real: EmojiCatalog by lazy {
        val dir = File(checkNotNull(System.getProperty("emoji.assets")))
        EmojiData.load({ File(dir, it).inputStream() }).catalog
    }

    @Test
    fun `the generated catalogue has every category and plausible sizes`() {
        assertEquals(EmojiCategory.entries, real.groups.map { it.category })
        val total = real.groups.sumOf { it.entries.size }
        assertTrue(total in 1700..2200, "total=$total")
        assertTrue(real.group(EmojiCategory.FLAGS).size > 250)
        assertEquals("😀", real.group(EmojiCategory.SMILEYS).first().emoji)
    }

    @Test
    fun `hand emoji have five single tone forms and tones apply`() {
        val hand = checkNotNull(real.entryOf(wave))
        assertEquals(5, hand.variants.size)
        assertEquals(tones[1], hand.forTone(SkinTone.MEDIUM_LIGHT))
    }

    @Test
    fun `every variant belongs to a base emoji and tone forms of hair variants attach`() {
        val all = real.groups.flatMap { it.entries }
        all.filter { it.hasTones }.forEach { entry ->
            assertTrue(
                entry.variants.size in 1..5,
                "${entry.emoji} has ${entry.variants.size} variants"
            )
        }
        // "man: red hair" has tone forms; the plain emoji is never listed as its own variant.
        assertTrue(all.none { it.emoji in it.variants })
    }
}
