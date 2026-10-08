// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.clipboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The clipboard history rules. Nothing is stored while [isPrivate] is true, for clips flagged
 * sensitive, when the history is switched off, or for blank or oversized text.
 */
class ClipboardHistory(
    private val store: ClipStore,
    private val policy: () -> ClipboardPolicy,
    private val isPrivate: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val lock = Mutex()

    val items: Flow<List<ClipItem>> get() = store.observe()

    /** The system clipboard changed. Returns true when the text was stored. */
    suspend fun onClip(text: CharSequence?, sensitive: Boolean): Boolean {
        val value = text?.toString()
        val current = policy()
        if (!current.enabled || isPrivate() || sensitive) return false
        if (value.isNullOrBlank() || value.length > ClipboardPolicy.MAX_CLIP_CHARS) return false
        lock.withLock {
            store.record(value, now())
            prune(current)
        }
        return true
    }

    /** Applies the retention and the item limit; called on every copy and when the panel opens. */
    suspend fun prune() = lock.withLock { prune(policy()) }

    private suspend fun prune(current: ClipboardPolicy) {
        current.retention.cutoff(now())?.let { store.deleteUnpinnedCopiedBefore(it) }
        store.trimUnpinnedTo(current.maxItems.coerceIn(ClipboardPolicy.MAX_ITEMS_RANGE))
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = lock.withLock { store.setPinned(id, pinned) }

    suspend fun delete(id: Long) = lock.withLock { store.delete(id) }

    /** Clears the history; pinned clips are the person's own choice to keep and stay. */
    suspend fun clearAll() = lock.withLock { store.deleteUnpinned() }
}
