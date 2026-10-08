// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.emoji

/** The most recently used emoji, newest first, kept as one space-separated string in the settings. */
object EmojiRecents {
    const val MAX = 32

    fun parse(stored: String): List<String> = stored.split(' ').filter { it.isNotEmpty() }.take(MAX)

    fun serialize(recents: List<String>): String = recents.take(MAX).joinToString(" ")

    /** [emoji] moves to the front; the list never repeats an emoji or grows past [MAX]. */
    fun add(stored: String, emoji: String): String {
        if (emoji.isBlank() || emoji.any { it.isWhitespace() }) return stored
        return serialize(listOf(emoji) + parse(stored).filterNot { it == emoji })
    }
}
