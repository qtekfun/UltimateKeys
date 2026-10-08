// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.panels

import com.qtekfun.ultimatekeys.clipboard.ClipboardHistory
import com.qtekfun.ultimatekeys.clipboard.ClipboardPolicy
import com.qtekfun.ultimatekeys.clipboard.MemoryClipStore
import com.qtekfun.ultimatekeys.clipboard.Retention
import com.qtekfun.ultimatekeys.core.FakeSettingsRepository
import com.qtekfun.ultimatekeys.emoji.EmojiAnnotation
import com.qtekfun.ultimatekeys.emoji.EmojiCatalog
import com.qtekfun.ultimatekeys.emoji.EmojiCategory
import com.qtekfun.ultimatekeys.emoji.EmojiData
import com.qtekfun.ultimatekeys.emoji.EmojiSearch
import com.qtekfun.ultimatekeys.emoji.EmojiSearchIndex
import com.qtekfun.ultimatekeys.emoji.SkinTone
import com.qtekfun.ultimatekeys.layouts.LayoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PanelsControllerTest {
    private val wave = "👋"
    private val waveLight = wave + "🏻"
    private val grin = "😀"

    private class Rig(scope: TestScope) {
        val settings = FakeSettingsRepository()
        val inserted = mutableListOf<String>()
        var private = false
        var loads = 0
        var now = 1_000L
        val store = MemoryClipStore()
        val history = ClipboardHistory(
            store,
            { ClipboardPolicy(retention = Retention.FOREVER) },
            { private },
            { now }
        )
        private val data = EmojiData(
            EmojiCatalog.parse("@smileys\n😀\n@people\n👋 👋🏻\n"),
            EmojiSearch(
                listOf(
                    EmojiSearchIndex(
                        listOf(
                            EmojiAnnotation("😀", "grinning face", listOf("smile")),
                            EmojiAnnotation("👋", "waving hand", listOf("hello"))
                        )
                    )
                )
            )
        )
        val controller = PanelsController(
            scope = scope.backgroundScope,
            settings = settings,
            currentSettings = { settings.settings.value },
            history = history,
            loadEmoji = {
                loads++
                data
            },
            isPrivate = { private },
            insert = { inserted.add(it) }
        )
    }

    @Test
    fun `opening the emoji panel loads the data once`() = runTest {
        val rig = Rig(this)
        assertEquals(PanelKind.NONE, rig.controller.kind.value)
        rig.controller.open(PanelKind.EMOJI)
        runCurrent()
        rig.controller.close()
        rig.controller.open(PanelKind.EMOJI)
        runCurrent()
        assertEquals(1, rig.loads)
        assertNotNull(rig.controller.emoji.value)
        assertEquals(PanelKind.EMOJI, rig.controller.kind.value)
    }

    @Test
    fun `a first visit starts on smileys and later on the recents`() = runTest {
        val rig = Rig(this)
        rig.controller.open(PanelKind.EMOJI)
        assertEquals(EmojiCategory.SMILEYS, rig.controller.category.value)
        rig.controller.pickText(grin)
        runCurrent()
        rig.controller.selectCategory(null)
        rig.controller.close()
        rig.controller.open(PanelKind.EMOJI)
        assertNull(rig.controller.category.value)
    }

    @Test
    fun `picking inserts the emoji in the chosen tone and remembers it`() = runTest {
        val rig = Rig(this)
        rig.controller.open(PanelKind.EMOJI)
        runCurrent()
        val entry = checkNotNull(
            rig.controller.emoji.value
        ).catalog.group(EmojiCategory.PEOPLE).single()
        rig.controller.pick(entry)
        assertEquals(listOf(wave), rig.inserted)
        rig.controller.cycleSkinTone()
        runCurrent()
        assertEquals(SkinTone.LIGHT, rig.controller.skinTone())
        rig.controller.pick(entry)
        runCurrent()
        assertEquals(listOf(wave, waveLight), rig.inserted)
        assertEquals(listOf(waveLight, wave), rig.controller.recents())
    }

    @Test
    fun `private mode inserts but never records recents`() = runTest {
        val rig = Rig(this)
        rig.private = true
        rig.controller.pickText(grin)
        runCurrent()
        assertEquals(listOf(grin), rig.inserted)
        assertEquals(emptyList<String>(), rig.controller.recents())
    }

    @Test
    fun `the skin tone cycles through all tones and back`() = runTest {
        val rig = Rig(this)
        repeat(SkinTone.entries.size) {
            rig.controller.cycleSkinTone()
            runCurrent()
        }
        assertEquals(SkinTone.NONE, rig.controller.skinTone())
    }

    @Test
    fun `search types, deletes and finds in both words and names`() = runTest {
        val rig = Rig(this)
        rig.controller.open(PanelKind.EMOJI)
        rig.controller.openSearch()
        runCurrent()
        assertTrue(rig.controller.searching.value)
        "smi".forEach { rig.controller.typeQuery(it.toString()) }
        assertEquals("smi", rig.controller.query.value)
        assertEquals(listOf(grin), rig.controller.results.value.map { it.emoji })
        rig.controller.deleteQueryChar()
        assertEquals("sm", rig.controller.query.value)
        rig.controller.closeSearch()
        assertFalse(rig.controller.searching.value)
        assertEquals("", rig.controller.query.value)
        assertTrue(rig.controller.results.value.isEmpty())
    }

    @Test
    fun `closing the panel ends the search`() = runTest {
        val rig = Rig(this)
        rig.controller.open(PanelKind.EMOJI)
        rig.controller.openSearch()
        runCurrent()
        rig.controller.typeQuery("hel")
        rig.controller.close()
        assertEquals("", rig.controller.query.value)
        assertFalse(rig.controller.searching.value)
        assertEquals(PanelKind.NONE, rig.controller.kind.value)
    }

    @Test
    fun `a search typed before the data arrives is answered when it does`() = runTest {
        val rig = Rig(this)
        rig.controller.openSearch()
        rig.controller.typeQuery("hel")
        assertTrue(rig.controller.results.value.isEmpty())
        runCurrent()
        assertEquals(listOf(wave), rig.controller.results.value.map { it.emoji })
    }

    @Test
    fun `clipboard actions paste, pin, delete and clear`() = runTest {
        val rig = Rig(this)
        rig.history.onClip("one", false)
        rig.now++
        rig.history.onClip("two", false)
        rig.controller.open(PanelKind.CLIPBOARD)
        runCurrent()
        val items = rig.history.items.first()
        rig.controller.paste(items.first())
        assertEquals(listOf("two"), rig.inserted)
        rig.controller.togglePin(items.first { it.text == "one" })
        runCurrent()
        assertTrue(rig.history.items.first().first().pinned)
        rig.controller.delete(items.first { it.text == "two" })
        runCurrent()
        assertEquals(listOf("one"), rig.history.items.first().map { it.text })
        rig.controller.togglePin(rig.history.items.first().single())
        rig.controller.clearAll()
        runCurrent()
        assertTrue(rig.history.items.first().isEmpty())
    }

    @Test
    fun `search letters come from the layout without numbers or actions`() {
        val rows = PanelLetters.rows(LayoutRepository.pages("es_qwerty", true).letters)
        assertEquals(3, rows.size)
        assertEquals("q", rows.first().first().label)
        assertTrue(rows.flatten().all { key -> key.label.any(Char::isLetter) })
    }
}
