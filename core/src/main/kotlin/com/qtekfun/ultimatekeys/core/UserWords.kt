// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A word the user added on purpose, with the language it belongs to (for example `es`). */
data class UserWord(val language: String, val word: String)

/**
 * The user's own words, kept in a plain text file (`language<TAB>word` per line) so they survive
 * engine changes and can be exported. The suggestion engine is told about them separately.
 */
class UserWordsRepository(private val file: File) {
    private val mutable = MutableStateFlow(load())
    val words: StateFlow<List<UserWord>> = mutable.asStateFlow()

    @Synchronized
    fun add(language: String, word: String): Boolean {
        val entry = UserWord(language.trim().lowercase(), word.trim())
        if (!isAcceptable(entry) || entry in mutable.value) return false
        save(mutable.value + entry)
        return true
    }

    @Synchronized
    fun remove(entry: UserWord) {
        save(mutable.value - entry)
    }

    @Synchronized
    fun clear() {
        save(emptyList())
    }

    /** One `language<TAB>word` line per word. */
    fun exportText(): String = mutable.value.joinToString("\n") { "${it.language}\t${it.word}" }

    /** Adds the words of [text] (the export format, or one bare word per line for [defaultLanguage]). */
    @Synchronized
    fun importText(text: String, defaultLanguage: String): Int {
        var added = 0
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val parts = line.split('\t', limit = 2)
            val entry = if (parts.size == 2) {
                UserWord(parts[0].lowercase(), parts[1].trim())
            } else {
                UserWord(defaultLanguage, line)
            }
            if (isAcceptable(entry) && entry !in mutable.value) {
                mutable.value = mutable.value + entry
                added++
            }
        }
        if (added > 0) save(mutable.value)
        return added
    }

    private fun isAcceptable(entry: UserWord) =
        entry.language.length in LANGUAGE_LENGTHS && entry.word.length in 1..MAX_WORD_LENGTH &&
            entry.word.none { it.isWhitespace() || it.isISOControl() }

    private fun save(list: List<UserWord>) {
        mutable.value = list
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(list.joinToString("\n") { "${it.language}\t${it.word}" })
        check(temp.renameTo(file)) { "Cannot save the user dictionary" }
    }

    private fun load(): List<UserWord> {
        if (!file.isFile) return emptyList()
        return file.readLines().mapNotNull { line ->
            line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { UserWord(it[0], it[1]) }
        }
    }

    private companion object {
        val LANGUAGE_LENGTHS = 2..3
        const val MAX_WORD_LENGTH = 48
    }
}
