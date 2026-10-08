// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.qtekfun.ultimatekeys.style.Presets
import com.qtekfun.ultimatekeys.style.Style
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DataStoreStyleRepositoryTest {
    private fun repo(scope: CoroutineScope): DataStoreStyleRepository {
        val file = Files.createTempFile("styles", ".preferences_pb").toFile().apply { delete() }
        file.deleteOnExit()
        return DataStoreStyleRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @Test
    fun `starts on the default preset with no custom styles`() = runTest {
        val repo = repo(backgroundScope)
        assertEquals(Presets.default, repo.active.first())
        assertEquals(emptyList<Style>(), repo.custom.first())
    }

    @Test
    fun `a saved style survives and can be selected`() = runTest {
        val repo = repo(backgroundScope)
        val saved = repo.save(Style(id = "mine", name = "Mine"))
        repo.select(saved.id)
        assertEquals("Mine", repo.active.first().name)
        assertEquals(listOf("mine"), repo.custom.first().map { it.id })
    }

    @Test
    fun `saving with a preset id gives the copy its own id`() = runTest {
        val repo = repo(backgroundScope)
        val saved = repo.save(Presets.Soft.copy(name = "My soft"))
        assertNotEquals("soft", saved.id)
        assertEquals(Presets.Soft.keys, repo.custom.first().single().keys)
    }

    @Test
    fun `deleting the active style returns to the default`() = runTest {
        val repo = repo(backgroundScope)
        repo.save(Style(id = "a"))
        repo.select("a")
        repo.delete("a")
        assertEquals(Presets.default.id, repo.activeId.first())
        assertEquals(emptyList<Style>(), repo.custom.first())
    }
}
