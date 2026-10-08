// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StyleRepositoryTest {
    @Test
    fun `starts on the default preset`() = runTest {
        assertEquals(Presets.default, InMemoryStyleRepository().active.first())
    }

    @Test
    fun `saving a custom style and selecting it makes it active`() = runTest {
        val repo = InMemoryStyleRepository()
        val saved = repo.save(Style(id = "mine", name = "Mine"))
        repo.select(saved.id)
        assertEquals("Mine", repo.active.first().name)
        assertEquals(listOf(saved), repo.custom.first())
    }

    @Test
    fun `a custom style cannot take a preset id`() = runTest {
        val repo = InMemoryStyleRepository()
        val saved = repo.save(Presets.Ultimate.copy(name = "Copy"))
        assertNotEquals(Presets.Ultimate.id, saved.id)
        assertTrue(Presets.isPreset(Presets.Ultimate.id))
        assertEquals(1, repo.custom.first().size)
    }

    @Test
    fun `saving the same id twice replaces it`() = runTest {
        val repo = InMemoryStyleRepository()
        repo.save(Style(id = "a", name = "One"))
        repo.save(Style(id = "a", name = "Two"))
        assertEquals(listOf("Two"), repo.custom.first().map { it.name })
    }

    @Test
    fun `deleting the active style falls back to the default`() = runTest {
        val repo = InMemoryStyleRepository()
        repo.save(Style(id = "a"))
        repo.select("a")
        repo.delete("a")
        assertEquals(Presets.default.id, repo.activeId.first())
        assertEquals(Presets.default, repo.active.first())
    }

    @Test
    fun `an unknown active id resolves to the default`() = runTest {
        assertEquals(Presets.default, InMemoryStyleRepository("gone").active.first())
    }

    @Test
    fun `a list of styles round trips and bad entries are dropped`() {
        val list = listOf(Style(id = "a", name = "A"), Style(id = "b", name = "B"))
        assertEquals(list.map { it.sanitized() }, StyleCodec.decodeAll(StyleCodec.encodeAll(list)))
        assertEquals(emptyList<Style>(), StyleCodec.decodeAll("nonsense"))
        assertEquals(
            1,
            StyleCodec.decodeAll(
                "[{\"id\":\"x\",\"light\":3}, " +
                    StyleCodec.encodeAll(list.take(1)).trim().removePrefix("[").removeSuffix("]") +
                    "]"
            ).size
        )
    }
}
