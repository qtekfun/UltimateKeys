// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.clipboard

import kotlinx.coroutines.flow.Flow

/** Where the history lives. Room in the app, in memory in tests. */
interface ClipStore {
    /** Pinned clips first, then the most recently copied. */
    fun observe(): Flow<List<ClipItem>>

    /** Adds [text], or moves it to the top when it is already there (keeping its pin). */
    suspend fun record(text: String, now: Long)

    suspend fun setPinned(id: Long, pinned: Boolean)

    suspend fun delete(id: Long)

    /** Removes every clip that is not pinned. */
    suspend fun deleteUnpinned()

    suspend fun deleteUnpinnedCopiedBefore(cutoff: Long)

    /** Keeps only the [max] most recent unpinned clips. */
    suspend fun trimUnpinnedTo(max: Int)
}
