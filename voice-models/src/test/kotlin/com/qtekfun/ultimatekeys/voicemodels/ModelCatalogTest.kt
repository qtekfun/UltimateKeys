// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voicemodels

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ModelCatalogTest {
    private val shipped = File("src/main/assets/models.json").readText()

    @Test
    fun `the shipped catalog parses and pins both models`() {
        val catalog = ModelCatalog.parse(shipped)
        assertEquals(listOf("base", "small"), catalog.models.map { it.id })
        val base = catalog.find("base")!!
        assertEquals("ggml-base-q5_1.bin", base.file)
        assertEquals(59_707_625L, base.bytes)
        assertEquals("MIT", base.license)
        assertTrue(base.url.contains(catalog.source.commit))
        assertTrue(
            catalog.models.all {
                it.url.startsWith("https://huggingface.co/ggerganov/whisper.cpp/resolve/")
            }
        )
        assertEquals(190_085_487L, catalog.find("small")!!.bytes)
    }

    @Test
    fun `lookups by sha and file`() {
        val catalog = ModelCatalog.parse(shipped)
        val small = catalog.find("small")!!
        assertEquals(small, catalog.bySha256(small.sha256.uppercase()))
        assertEquals(small, catalog.byFile("ggml-small-q5_1.bin"))
        assertNull(catalog.find("tiny"))
        assertNull(catalog.bySha256("0".repeat(64)))
    }

    @Test
    fun `unknown keys are ignored so the file can grow`() {
        val text = shipped.replace("\"schema\": 1,", "\"schema\": 1, \"note\": \"x\",")
        assertNotNull(ModelCatalog.parse(text))
    }

    @Test
    fun `broken catalogs are refused`() {
        val sha = "a".repeat(64)
        fun model(
            id: String = "m",
            file: String = "m.bin",
            bytes: Long = 5,
            hash: String = sha,
            url: String = "https://x.test/m.bin"
        ) =
            """{"id":"$id","name":"M","file":"$file","bytes":$bytes,"sha256":"$hash","url":"$url","license":"MIT"}"""

        fun catalog(vararg models: String, schema: Int = 1) =
            """{"schema":$schema,"source":{"repository":"r","commit":"c"},"models":[${models.joinToString(
                ","
            )}]}"""
        assertNotNull(ModelCatalog.parse(catalog(model())))
        listOf(
            "not json",
            catalog(model(), schema = 2),
            catalog(),
            catalog(model(), model(file = "n.bin")),
            catalog(model(), model(id = "n")),
            catalog(model(hash = "XYZ")),
            catalog(model(hash = sha.uppercase())),
            catalog(model(bytes = 0)),
            catalog(model(file = "../evil.bin")),
            catalog(model(file = "m.txt")),
            catalog(model(url = "http://x.test/m.bin")),
            catalog(model(id = ""))
        ).forEach { text ->
            assertThrows<IllegalArgumentException>(text) { ModelCatalog.parse(text) }
        }
    }
}
