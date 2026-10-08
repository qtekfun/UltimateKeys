// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.core

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class UserWordsTest {
    @TempDir
    lateinit var tmp: File

    private fun repo() = UserWordsRepository(File(tmp, "sub/user_words.txt"))

    @Test
    fun `adds, rejects bad and duplicate words, persists`() {
        val r = repo()
        assertTrue(r.add("ES", " Ultimate "))
        assertFalse(r.add("es", "Ultimate"))
        assertFalse(r.add("es", "two words"))
        assertFalse(r.add("es", ""))
        assertFalse(r.add("espanol", "x"))
        assertFalse(r.add("es", "a".repeat(49)))
        assertEquals(listOf(UserWord("es", "Ultimate")), r.words.value)
        assertEquals(listOf(UserWord("es", "Ultimate")), repo().words.value)
    }

    @Test
    fun `removes and clears`() {
        val r = repo()
        r.add("es", "uno")
        r.add("en", "two")
        r.remove(UserWord("es", "uno"))
        assertEquals(listOf(UserWord("en", "two")), r.words.value)
        r.clear()
        assertEquals(emptyList<UserWord>(), repo().words.value)
    }

    @Test
    fun `export and import round trip, bare words use the default language`() {
        val r = repo()
        r.add("es", "uno")
        r.add("en", "two")
        val text = r.exportText()
        val other = UserWordsRepository(File(tmp, "other.txt"))
        assertEquals(2, other.importText(text, "es"))
        assertEquals(r.words.value, other.words.value)
        assertEquals(1, other.importText("nuevo\n\nuno\n  \n", "es"))
        assertTrue(UserWord("es", "nuevo") in other.words.value)
        assertEquals(0, other.importText("", "es"))
    }

    @Test
    fun `a corrupt file is read leniently`() {
        val f = File(tmp, "bad.txt")
        f.writeText("garbage\nes\tok\n")
        assertEquals(listOf(UserWord("es", "ok")), UserWordsRepository(f).words.value)
    }
}
