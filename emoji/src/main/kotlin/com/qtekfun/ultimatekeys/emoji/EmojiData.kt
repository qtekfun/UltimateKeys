// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.emoji

import android.content.Context
import android.graphics.Paint
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the emoji panel needs: what to show and how to search it. */
class EmojiData(val catalog: EmojiCatalog, val search: EmojiSearch) {
    companion object {
        private const val DIR = "emoji"

        /**
         * Reads the generated assets through [open] (an asset path to its stream). [languages] is in
         * preference order. [canDraw] hides emoji newer than the device's emoji font.
         */
        fun load(
            open: (String) -> InputStream,
            languages: List<String> = listOf("en", "es"),
            canDraw: (String) -> Boolean = { true }
        ): EmojiData {
            fun read(name: String) =
                open("$DIR/$name").use { it.readBytes().toString(Charsets.UTF_8) }
            val catalog = EmojiCatalog.parse(read("catalog.txt")).filtered(canDraw)
            val indexes = languages.map { EmojiSearchIndex.parse(read("search_$it.tsv")) }
            return EmojiData(catalog, EmojiSearch(indexes))
        }
    }
}

/** Loads [EmojiData] from the app's assets off the main thread, hiding emoji the device cannot draw. */
suspend fun loadEmojiData(context: Context, languages: List<String>): EmojiData =
    withContext(Dispatchers.Default) {
        val paint = Paint()
        val assets = context.applicationContext.assets
        EmojiData.load(assets::open, languages) { paint.hasGlyph(it) }
    }
