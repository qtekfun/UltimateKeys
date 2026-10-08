// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.clipboard

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClipboardHistoryTest {
    private var clock = START
    private var private = false
    private var policy = ClipboardPolicy(retention = Retention.FOREVER, maxItems = 50)
    private val store = MemoryClipStore()
    private val history = ClipboardHistory(store, { policy }, { private }, { clock })

    private suspend fun texts() = history.items.first().map { it.text }

    private suspend fun copy(text: String, sensitive: Boolean = false): Boolean {
        clock += 1
        return history.onClip(text, sensitive)
    }

    @Test
    fun `newest first and a repeated copy moves to the top without duplicating`() = runTest {
        copy("a")
        copy("b")
        copy("a")
        assertEquals(listOf("a", "b"), texts())
    }

    @Test
    fun `nothing is stored while private mode is on`() = runTest {
        private = true
        assertFalse(copy("secret"))
        assertEquals(emptyList<String>(), texts())
        private = false
        assertTrue(copy("fine"))
        assertEquals(listOf("fine"), texts())
    }

    @Test
    fun `sensitive clips are never stored`() = runTest {
        assertFalse(copy("hunter2", sensitive = true))
        assertEquals(emptyList<String>(), texts())
    }

    @Test
    fun `blank null oversized and disabled history store nothing`() = runTest {
        assertFalse(copy("   "))
        assertFalse(history.onClip(null, sensitive = false))
        assertFalse(copy("x".repeat(ClipboardPolicy.MAX_CLIP_CHARS + 1)))
        assertTrue(copy("x".repeat(ClipboardPolicy.MAX_CLIP_CHARS)))
        policy = policy.copy(enabled = false)
        assertFalse(copy("off"))
        assertEquals(1, texts().size)
    }

    @Test
    fun `retention drops expired unpinned clips and keeps pinned ones`() = runTest {
        policy = policy.copy(retention = Retention.ONE_HOUR)
        copy("old")
        copy("old pinned")
        val pinned = history.items.first().first { it.text == "old pinned" }
        history.setPinned(pinned.id, true)
        clock += Retention.ONE_HOUR.millis!! + 1
        copy("new")
        assertEquals(listOf("old pinned", "new"), texts())
    }

    @Test
    fun `a clip exactly at the retention limit is kept`() = runTest {
        policy = policy.copy(retention = Retention.ONE_DAY)
        copy("edge")
        val copiedAt = history.items.first().single().copiedAt
        clock = copiedAt + Retention.ONE_DAY.millis!!
        history.prune()
        assertEquals(listOf("edge"), texts())
        clock += 1
        history.prune()
        assertEquals(emptyList<String>(), texts())
    }

    @Test
    fun `forever never expires`() = runTest {
        copy("keep")
        clock += 1000L * 24 * 3_600_000
        history.prune()
        assertEquals(listOf("keep"), texts())
    }

    @Test
    fun `max items trims the oldest unpinned and pinned do not count`() = runTest {
        policy = policy.copy(maxItems = ClipboardPolicy.MAX_ITEMS_RANGE.first)
        copy("p")
        history.setPinned(history.items.first().single().id, true)
        repeat(7) { copy("c$it") }
        val kept = texts()
        assertEquals(listOf("p", "c6", "c5", "c4", "c3", "c2"), kept)
    }

    @Test
    fun `max items is clamped to the supported range`() = runTest {
        policy = policy.copy(maxItems = 0)
        repeat(8) { copy("c$it") }
        assertEquals(ClipboardPolicy.MAX_ITEMS_RANGE.first, texts().size)
    }

    @Test
    fun `pin keeps a clip first and unpin returns it to time order`() = runTest {
        copy("a")
        copy("b")
        val a = history.items.first().first { it.text == "a" }
        history.setPinned(a.id, true)
        assertEquals(listOf("a", "b"), texts())
        copy("c")
        assertEquals(listOf("a", "c", "b"), texts())
        history.setPinned(a.id, false)
        assertEquals(listOf("c", "b", "a"), texts())
    }

    @Test
    fun `recopying a pinned clip keeps it pinned`() = runTest {
        copy("a")
        history.setPinned(history.items.first().single().id, true)
        copy("a")
        assertTrue(history.items.first().single().pinned)
    }

    @Test
    fun `delete removes one clip`() = runTest {
        copy("a")
        copy("b")
        history.delete(history.items.first().first { it.text == "a" }.id)
        assertEquals(listOf("b"), texts())
    }

    @Test
    fun `clear all removes unpinned clips only`() = runTest {
        copy("a")
        copy("b")
        history.setPinned(history.items.first().first { it.text == "a" }.id, true)
        history.clearAll()
        assertEquals(listOf("a"), texts())
    }

    @Test
    fun `retention ids round trip and unknown ids fall back to the default`() {
        Retention.entries.forEach { assertEquals(it, Retention.fromId(it.id)) }
        assertEquals(Retention.DEFAULT, Retention.fromId("fortnight"))
        assertEquals(Retention.DEFAULT, Retention.fromId(null))
        assertNull(Retention.FOREVER.cutoff(START))
        assertEquals(START - 3_600_000L, Retention.ONE_HOUR.cutoff(START))
    }

    @Test
    fun `only an explicit true flag is sensitive`() {
        assertTrue(ClipSensitivity.isSensitive(true))
        assertFalse(ClipSensitivity.isSensitive(false))
        assertFalse(ClipSensitivity.isSensitive(null))
    }

    private companion object {
        const val START = 1_000_000_000L
    }
}
