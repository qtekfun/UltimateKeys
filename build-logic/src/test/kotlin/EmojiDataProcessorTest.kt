// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmojiDataProcessorTest {
    private val sample = """
        # group: Smileys & Emotion
        # subgroup: face-smiling
        1F600                                                  ; fully-qualified     # 😀 E1.0 grinning face
        263A                                                   ; unqualified         # ☺ E0.6 smiling face
        263A FE0F                                              ; fully-qualified     # ☺️ E0.6 smiling face
        # group: People & Body
        1F44B                                                  ; fully-qualified     # 👋 E0.6 waving hand
        1F44B 1F3FB                                            ; fully-qualified     # 👋🏻 E1.0 waving hand: light skin tone
        1F44B 1F3FF                                            ; fully-qualified     # 👋🏿 E1.0 waving hand: dark skin tone
        1F468                                                  ; fully-qualified     # 👨 E0.6 man
        1F468 200D 1F9B0                                       ; fully-qualified     # 👨‍🦰 E11.0 man: red hair
        1F468 1F3FB 200D 1F9B0                                 ; fully-qualified     # 👨🏻‍🦰 E11.0 man: light skin tone, red hair
        1F48F                                                  ; fully-qualified     # 💏 E0.6 kiss
        1F469 200D 2764 FE0F 200D 1F48B 200D 1F468             ; fully-qualified     # 👩‍❤️‍💋‍👨 E2.0 kiss: woman, man
        1F469 1F3FB 200D 2764 FE0F 200D 1F48B 200D 1F468 1F3FB ; fully-qualified     # 👩🏻‍❤️‍💋‍👨🏻 E13.1 kiss: woman, man, light skin tone
        1F91D                                                  ; fully-qualified     # 🤝 E3.0 handshake
        1FAF1 1F3FB 200D 1FAF2 1F3FC                           ; fully-qualified     # 🫱🏻‍🫲🏼 E14.0 handshake: light skin tone, medium-light skin tone
        # group: Component
        1F3FB                                                  ; component           # 🏻 E1.0 light skin tone
        # group: Flags
        1F1EA 1F1F8                                            ; fully-qualified     # 🇪🇸 E0.6 flag: Spain
    """.trimIndent()

    private fun parsed() = EmojiDataProcessor.parseEmojiTest(sample)

    private fun cp(vararg codes: Int) = codes.joinToString("") { String(Character.toChars(it)) }

    @Test
    fun `keeps fully qualified emoji of keyboard groups in order`() {
        val groups = parsed()
        assertEquals(listOf("smileys", "people", "flags"), groups.map { it.id })
        assertEquals(listOf(cp(0x1F600), "☺️"), groups[0].emojis.map { it.emoji })
    }

    @Test
    fun `single tone forms attach to the base emoji`() {
        val wave = parsed()[1].emojis.first { it.name == "waving hand" }
        assertEquals(listOf(cp(0x1F44B, 0x1F3FB), cp(0x1F44B, 0x1F3FF)), wave.variants)
    }

    @Test
    fun `tone in the middle or at the end of a longer name finds its base`() {
        val people = parsed()[1].emojis
        assertEquals(1, people.first { it.name == "man: red hair" }.variants.size)
        assertEquals(1, people.first { it.name == "kiss: woman, man" }.variants.size)
    }

    @Test
    fun `two tone combinations and toned entries are not separate emoji`() {
        val people = parsed()[1].emojis
        assertTrue(people.first { it.name == "handshake" }.variants.isEmpty())
        assertTrue(people.none { it.name.contains("skin tone") })
    }

    @Test
    fun `annotations key ignores the variation selector and merges derived files`() {
        val json = """{"annotations":{"identity":{"language":"en"},"annotations":{
            "☺": {"default": ["blush", "face"], "tts": ["smiling face"]}}}}"""
        val derived = """{"annotationsDerived":{"annotations":{
            "🇪🇸": {"default": ["flag"], "tts": ["flag: Spain"]}}}}"""
        val all = EmojiDataProcessor.parseAnnotations(derived) + EmojiDataProcessor.parseAnnotations(json)
        assertEquals("smiling face", all.getValue("☺").name)
        assertEquals(listOf("flag"), all.getValue("🇪🇸").keywords)
    }

    @Test
    fun `catalogue lists groups then emoji with variants`() {
        val text = EmojiDataProcessor.renderCatalog(parsed())
        val lines = text.lines().filterNot { it.startsWith("#") || it.isEmpty() }
        assertEquals("@smileys", lines[0])
        assertTrue(lines.contains("@people"))
        val wave = lines.first { it.startsWith(cp(0x1F44B)) && it.contains(' ') }
        assertEquals(3, wave.split(' ').size)
    }

    @Test
    fun `search lines carry name and keywords and skip unannotated emoji`() {
        val annotations = mapOf(
            "☺" to EmojiDataProcessor.Annotation("smiling face", listOf("smiling face", "blush", "blush", "a|b"))
        )
        val lines = EmojiDataProcessor.renderSearch(parsed(), annotations).lines()
            .filterNot { it.startsWith("#") || it.isEmpty() }
        assertEquals(1, lines.size)
        val (emoji, name, keywords) = lines.single().split('\t')
        assertEquals("☺️", emoji)
        assertEquals("smiling face", name)
        assertEquals("blush|a b", keywords)
        assertFalse(lines.single().contains("smiling face|"))
    }
}
