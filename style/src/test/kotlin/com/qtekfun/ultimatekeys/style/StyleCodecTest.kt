// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.style

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StyleCodecTest {
    private fun loaded(text: String): Style =
        (StyleCodec.decode(text) as StyleLoadResult.Loaded).style

    private fun error(text: String): StyleLoadError =
        (StyleCodec.decode(text) as StyleLoadResult.Failed).error

    @Test
    fun `a style survives a round trip unchanged`() {
        val style = Style(
            id = "x",
            name = "X",
            appearance = Appearance.DARK,
            keys = KeyShape(cornerRadiusDp = 3f, border = true, shadow = ShadowKind.ELEVATION),
            labels = LabelStyle(font = FontChoice.NUNITO, weight = 700),
            background = BackgroundStyle(
                kind = BackgroundKind.GRADIENT,
                lightColors = listOf(ArgbColor.rgb(0x112233), ArgbColor.rgb(0x445566)),
                darkColors = listOf(ArgbColor.rgb(0x010203), ArgbColor(0x80040506.toInt()))
            ),
            bottomRow = BottomRowStyle(BottomRowArrangement.SYMBOLS_GLOBE_SPACE_ENTER)
        )
        assertEquals(style.sanitized(), loaded(StyleCodec.encode(style)))
    }

    @Test
    fun `values are clamped on load`() {
        val text = StyleCodec.encode(
            Style()
        ).replace("\"cornerRadiusDp\": 8.0", "\"cornerRadiusDp\": 99.0")
            .replace("\"durationMs\": 180", "\"durationMs\": 99999")
        val style = loaded(text)
        assertEquals(28f, style.keys.cornerRadiusDp)
        assertEquals(500, style.motion.durationMs)
    }

    @Test
    fun `unknown keys are ignored and unknown enum values fall back to defaults`() {
        val text = StyleCodec.encode(Style()).replace("\"slide\"", "\"teleport\"")
            .replaceFirst("{", "{\"futureThing\": 1,")
        assertEquals(PanelTransition.SLIDE, loaded(text).motion.transition)
    }

    @Test
    fun `a version 1 file gains the gesture trail and the private tint`() {
        val v1 = StyleCodec.encode(Presets.Soft).lines().filterNot {
            "gestureTrail" in it || "privateTint" in it
        }.joinToString("\n")
            .replace("\"schemaVersion\": ${Style.CURRENT_SCHEMA_VERSION}", "\"schemaVersion\": 1")
            // The last palette entry loses its trailing comma neighbours; the encoder always writes commas.
            .replace(",\n    }", "\n    }")
        val migrated = loaded(v1)
        assertEquals(Style.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(Presets.Soft.light.keyAction, migrated.light.gestureTrail)
        assertEquals(Presets.Soft.dark.keyAction, migrated.dark.gestureTrail)
        assertEquals(Palette.DefaultLight.privateTint, migrated.light.privateTint)
        assertEquals(Palette.DefaultDark.privateTint, migrated.dark.privateTint)
    }

    @Test
    fun `a missing schema version is read as version 1`() {
        val text = StyleCodec.encode(Style()).lines().filterNot {
            "schemaVersion" in it
        }.joinToString("\n")
        assertEquals(Style.CURRENT_SCHEMA_VERSION, loaded(text).schemaVersion)
    }

    @Test
    fun `a newer schema is refused`() {
        val text = StyleCodec.encode(
            Style()
        ).replace("\"schemaVersion\": ${Style.CURRENT_SCHEMA_VERSION}", "\"schemaVersion\": 99")
        assertEquals(StyleLoadError.NewerSchema(99), error(text))
    }

    @Test
    fun `garbage and oversized input fail without throwing`() {
        assertEquals(StyleLoadError.NotJson, error("not json at all"))
        assertEquals(StyleLoadError.NotJson, error("[1, 2]"))
        assertEquals(StyleLoadError.TooLarge, error(" ".repeat(StyleCodec.MAX_BYTES + 1)))
        assertInstanceOf(
            StyleLoadError.Invalid::class.java,
            error("{\"keys\": {\"cornerRadiusDp\": \"wide\"}}")
        )
    }

    @Test
    fun `a bad colour is reported as invalid`() {
        val text = StyleCodec.encode(Style()).replace("#E6E8EC", "red")
        assertInstanceOf(StyleLoadError.Invalid::class.java, error(text))
    }

    @Test
    fun `text fields are trimmed and bounded`() {
        val style = Style(
            id = " ",
            name = "n".repeat(500),
            description = "d".repeat(500)
        ).sanitized()
        assertEquals("custom", style.id)
        assertEquals(Style.MAX_NAME, style.name.length)
        assertEquals(Style.MAX_DESCRIPTION, style.description.length)
    }

    @Test
    fun `gradients keep two or three stops and solids exactly one`() {
        val gradient = BackgroundStyle(
            kind = BackgroundKind.GRADIENT,
            lightColors = listOf(ArgbColor.rgb(1)),
            darkColors = List(5) { ArgbColor.rgb(it) },
            gradientAngleDegrees = -90
        ).sanitized()
        assertEquals(2, gradient.lightColors.size)
        assertEquals(3, gradient.darkColors.size)
        assertEquals(270, gradient.gradientAngleDegrees)
        val solid = BackgroundStyle(
            lightColors = emptyList(),
            darkColors = List(3) {
                ArgbColor.rgb(it)
            }
        ).sanitized()
        assertEquals(1, solid.lightColors.size)
        assertEquals(1, solid.darkColors.size)
    }

    @Test
    fun `image names cannot carry paths of unbounded length`() {
        val bg = BackgroundStyle(kind = BackgroundKind.IMAGE, imageName = "  ").sanitized()
        assertEquals(null, bg.imageName)
        assertTrue(
            BackgroundStyle(imageName = "a".repeat(999)).sanitized().imageName!!.length <= 120
        )
    }
}
