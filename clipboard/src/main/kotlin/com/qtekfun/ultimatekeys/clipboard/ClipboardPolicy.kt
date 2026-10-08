// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.clipboard

/** How long unpinned clips are kept (SPEC section 10). */
enum class Retention(val id: String, val millis: Long?) {
    ONE_HOUR("hour", HOUR_MS),
    ONE_DAY("day", DAY_MS),
    SEVEN_DAYS("week", WEEK_MS),
    FOREVER("forever", null);

    /** The oldest `copiedAt` that is still kept at [now], or null when nothing expires. */
    fun cutoff(now: Long): Long? = millis?.let { now - it }

    companion object {
        val DEFAULT = ONE_DAY

        /** Unknown ids (a newer or corrupted setting) fall back to the default. */
        fun fromId(id: String?): Retention = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS
private const val WEEK_MS = 7 * DAY_MS

/**
 * The person's clipboard settings. Pinned clips never expire and do not count against [maxItems].
 */
data class ClipboardPolicy(
    val enabled: Boolean = true,
    val retention: Retention = Retention.DEFAULT,
    val maxItems: Int = DEFAULT_MAX_ITEMS
) {
    companion object {
        const val DEFAULT_MAX_ITEMS = 50
        val MAX_ITEMS_RANGE = 5..500

        /** The choices offered in settings. */
        val MAX_ITEMS_CHOICES = listOf(10, 25, 50, 100, 200)

        /** Longer texts are not worth keeping (and would bloat the database). */
        const val MAX_CLIP_CHARS = 20_000
    }
}
