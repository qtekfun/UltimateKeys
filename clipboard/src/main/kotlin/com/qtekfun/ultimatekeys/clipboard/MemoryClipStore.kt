// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.clipboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [ClipStore] with the same ordering rules as the database. Used by tests and previews. */
class MemoryClipStore(initial: List<ClipItem> = emptyList()) : ClipStore {
    private val all = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observe(): Flow<List<ClipItem>> = all.map(::sorted)

    override suspend fun record(text: String, now: Long) {
        val existing = all.value.firstOrNull { it.text == text }
        all.value = if (existing == null) {
            all.value + ClipItem(nextId++, text, pinned = false, copiedAt = now)
        } else {
            all.value.map { if (it.id == existing.id) it.copy(copiedAt = now) else it }
        }
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        all.value = all.value.map { if (it.id == id) it.copy(pinned = pinned) else it }
    }

    override suspend fun delete(id: Long) {
        all.value = all.value.filterNot { it.id == id }
    }

    override suspend fun deleteUnpinned() {
        all.value = all.value.filter { it.pinned }
    }

    override suspend fun deleteUnpinnedCopiedBefore(cutoff: Long) {
        all.value = all.value.filter { it.pinned || it.copiedAt >= cutoff }
    }

    override suspend fun trimUnpinnedTo(max: Int) {
        val keep = all.value.filter { !it.pinned }.sortedWith(RECENT_FIRST).take(max).toSet()
        all.value = all.value.filter { it.pinned || it in keep }
    }

    private fun sorted(items: List<ClipItem>) =
        items.sortedWith(compareByDescending<ClipItem> { it.pinned }.then(RECENT_FIRST))

    private companion object {
        val RECENT_FIRST = compareByDescending<ClipItem> { it.copiedAt }.thenByDescending { it.id }
    }
}
