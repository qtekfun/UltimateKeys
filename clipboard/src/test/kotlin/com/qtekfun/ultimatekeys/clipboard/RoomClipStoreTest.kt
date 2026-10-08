// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.clipboard

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Room store against a real (in-memory) SQLite database: the queries, not only the rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomClipStoreTest {
    private val store = clipStore(RuntimeEnvironment.getApplication(), name = null)

    private fun texts() = runBlocking { store.observe().first().map { it.text } }

    @Test
    fun `record adds new text and moves a repeat to the top keeping its pin`() = runBlocking {
        store.record("a", 1)
        store.record("b", 2)
        val a = store.observe().first().first { it.text == "a" }
        store.setPinned(a.id, true)
        store.record("a", 3)
        val items = store.observe().first()
        assertEquals(listOf("a", "b"), items.map { it.text })
        assertTrue(items.first().pinned)
        assertEquals(3L, items.first().copiedAt)
    }

    @Test
    fun `pinned first then newest first`() = runBlocking {
        store.record("old", 1)
        store.record("new", 5)
        store.record("pinned", 0)
        store.setPinned(store.observe().first().first { it.text == "pinned" }.id, true)
        assertEquals(listOf("pinned", "new", "old"), texts())
    }

    @Test
    fun `retention deletes only unpinned clips before the cutoff`() = runBlocking {
        store.record("expired", 10)
        store.record("kept", 100)
        store.record("pinned old", 1)
        store.setPinned(store.observe().first().first { it.text == "pinned old" }.id, true)
        store.deleteUnpinnedCopiedBefore(50)
        assertEquals(listOf("pinned old", "kept"), texts())
    }

    @Test
    fun `trim keeps the newest unpinned clips and every pinned one`() = runBlocking {
        (1..6).forEach { store.record("c$it", it.toLong()) }
        store.record("p", 0)
        store.setPinned(store.observe().first().first { it.text == "p" }.id, true)
        store.trimUnpinnedTo(2)
        assertEquals(listOf("p", "c6", "c5"), texts())
    }

    @Test
    fun `delete and clear remove clips`() = runBlocking {
        store.record("a", 1)
        store.record("b", 2)
        store.record("c", 3)
        store.delete(store.observe().first().first { it.text == "b" }.id)
        store.setPinned(store.observe().first().first { it.text == "a" }.id, true)
        assertEquals(listOf("a", "c"), texts())
        store.deleteUnpinned()
        assertEquals(listOf("a"), texts())
    }
}
