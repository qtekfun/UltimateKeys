// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.suggest

import com.qtekfun.ultimatekeys.engine.KeyboardGeometry
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayoutGeometriesTest {
    private fun codePoints(geometry: KeyboardGeometry) = geometry.keys.map { it.codePoint }.toSet()

    @Test
    fun `the engine gets the Cyrillic keys for Russian`() {
        val geometry = LayoutGeometries.forLocale(Locale.forLanguageTag("ru"))
        assertTrue(
            codePoints(geometry).containsAll(
                "йцукенгшщзхъфывапролджэячсмитьбю".map {
                    it.code
                }
            )
        )
        assertTrue(codePoints(geometry).none { it < 0x400 })
        assertEquals(12, geometry.width / 100)
    }

    @Test
    fun `French is AZERTY and Greek has its letters`() {
        val french = LayoutGeometries.forLocale(Locale.forLanguageTag("fr"))
        val a = french.keys.first { it.codePoint == 'a'.code }
        val q = french.keys.first { it.codePoint == 'q'.code }
        assertTrue(a.y < q.y, "a is above q on AZERTY")
        val greek = LayoutGeometries.forLocale(Locale.forLanguageTag("el"))
        assertTrue(codePoints(greek).containsAll("ςερτυθιοπασδφγηξκλζχψωβνμ".map { it.code }))
    }

    @Test
    fun `the chosen layout variant decides and unknown languages stay QWERTY`() {
        val qwerty = LayoutGeometries.forLocale(Locale.forLanguageTag("fr")) { "fr_qwerty" }
        val a = qwerty.keys.first { it.codePoint == 'a'.code }
        val q = qwerty.keys.first { it.codePoint == 'q'.code }
        assertTrue(a.y > q.y)
        val other = LayoutGeometries.forLocale(Locale.forLanguageTag("xx"))
        assertEquals(KeyboardGeometry.qwertyFor(Locale.forLanguageTag("xx")), other)
    }
}
