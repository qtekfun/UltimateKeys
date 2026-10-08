// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.clipboard

/** One entry of the clipboard history. */
data class ClipItem(
    val id: Long,
    val text: String,
    val pinned: Boolean,
    /** Epoch milliseconds of the last time this text was copied. */
    val copiedAt: Long
)
