// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DirectoryModelSourceTest {
    @Test
    fun `picks the first model by name and ignores other files`() {
        val dir = createTempDirectory("models").toFile()
        try {
            val source = DirectoryModelSource(dir)
            assertNull(source.currentModel())
            File(dir, "notes.txt").writeText("x")
            File(dir, "ggml-small.bin").writeText("x")
            File(dir, "ggml-base.bin").writeText("x")
            File(dir, "sub.bin").mkdir()
            assertEquals("ggml-base.bin", source.currentModel()?.name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a missing directory means no model`() {
        assertNull(DirectoryModelSource(File("/does/not/exist")).currentModel())
    }
}
