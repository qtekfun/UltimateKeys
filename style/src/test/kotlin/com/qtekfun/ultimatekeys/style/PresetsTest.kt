// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PresetsTest {
    private val forbidden = listOf(
        "gboard", "google keyboard", "swiftkey", "samsung", "apple", "ios",
        "heliboard", "openboard", "florisboard", "microsoft"
    )

    @Test
    fun `there are at least seven presets with unique ids and names`() {
        assertTrue(Presets.all.size >= 7)
        assertEquals(Presets.all.size, Presets.all.map { it.id }.toSet().size)
        assertEquals(Presets.all.size, Presets.all.map { it.name }.toSet().size)
    }

    @Test
    fun `every preset is already valid and survives a round trip`() {
        Presets.all.forEach {
            assertEquals(it, it.sanitized(), it.id)
            assertEquals(
                it,
                (StyleCodec.decode(StyleCodec.encode(it)) as StyleLoadResult.Loaded).style,
                it.id
            )
        }
    }

    @Test
    fun `presets look clearly different from each other`() {
        // Compare the traits a person notices first; no two presets may share all of them.
        val looks = Presets.all.map {
            listOf(
                it.keys.cornerRadiusDp,
                it.keys.border,
                it.keys.shadow,
                it.labels.font,
                it.background.kind,
                it.light.keyLetter,
                it.feedback.popup
            )
        }
        assertEquals(looks.size, looks.toSet().size)
        assertTrue(Presets.all.map { it.keys.shadow }.toSet().size == ShadowKind.entries.size)
        assertTrue(Presets.all.any { it.keys.border })
        assertTrue(Presets.all.any { it.background.kind == BackgroundKind.GRADIENT })
        assertTrue(Presets.all.map { it.labels.font }.toSet().size >= 4)
    }

    @Test
    fun `labels are readable in every variant of every preset`() {
        Presets.all.forEach { style ->
            listOf(style.light to "light", style.dark to "dark").forEach { (p, variant) ->
                val where = "${style.id}/$variant"
                assertTrue(meetsAa(p.labelText, p.keyLetter), "label on letter key, $where")
                assertTrue(meetsAa(p.labelText, p.keyFunction), "label on function key, $where")
                assertTrue(meetsAa(p.actionLabelText, p.keyAction), "label on action key, $where")
                assertTrue(meetsAa(p.barText, p.barBackground), "suggestion text, $where")
                assertTrue(meetsAa(p.panelText, p.panelSurface), "panel text, $where")
                assertTrue(contrastRatio(p.hintText, p.keyLetter) >= 3.0, "hint, $where")
            }
        }
    }

    @Test
    fun `names and descriptions never mention other keyboard products`() {
        Presets.all.forEach { style ->
            val text = "${style.id} ${style.name} ${style.description}".lowercase()
            forbidden.forEach {
                assertTrue(!text.contains(Regex("\\b${Regex.escape(it)}\\b")), "${style.id}: $it")
            }
        }
    }

    @Test
    fun `lookup by id and the default`() {
        assertEquals(Presets.Soft, Presets.byId("soft"))
        assertEquals(null, Presets.byId("nope"))
        assertTrue(Presets.isPreset(Presets.default.id))
    }
}
