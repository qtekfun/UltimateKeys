// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UkLogicTest {
    @Test
    fun `the title does not collapse before it is measured`() {
        assertEquals(0f, TitleCollapse.fraction(scrollPx = 500f, titleHeightPx = 0f))
    }

    @Test
    fun `the collapse grows with the scroll and stays within zero and one`() {
        assertEquals(0f, TitleCollapse.fraction(0f, 100f))
        assertEquals(0.5f, TitleCollapse.fraction(30f, 100f), 1e-6f)
        assertEquals(1f, TitleCollapse.fraction(70f, 100f))
        assertEquals(1f, TitleCollapse.fraction(Float.MAX_VALUE, 100f))
        assertEquals(0f, TitleCollapse.fraction(-20f, 100f))
    }

    @Test
    fun `the large title fades out while the bar title fades in`() {
        assertEquals(1f, TitleCollapse.largeAlpha(0f))
        assertEquals(0f, TitleCollapse.largeAlpha(1f))
        assertEquals(0f, TitleCollapse.smallAlpha(0.25f))
        assertEquals(0f, TitleCollapse.smallAlpha(0.5f))
        assertEquals(1f, TitleCollapse.smallAlpha(1f))
        assertEquals(0.5f, TitleCollapse.separatorAlpha(0.5f))
    }

    @Test
    fun `two to four short labels are segments`() {
        assertEquals(
            ChoicePresentation.Segmented,
            ChoicePresentation.of(listOf("1 hour", "1 day", "7 days", "Forever"))
        )
        assertEquals(ChoicePresentation.Segmented, ChoicePresentation.of(listOf("Light", "Dark")))
    }

    @Test
    fun `long, many or single options are a picker`() {
        assertEquals(ChoicePresentation.Picker, ChoicePresentation.of(listOf("Only one")))
        assertEquals(ChoicePresentation.Picker, ChoicePresentation.of(List(5) { "A" }))
        assertEquals(
            ChoicePresentation.Picker,
            ChoicePresentation.of(listOf("Automatic (Spanish or English)", "Spanish", "English"))
        )
        assertEquals(
            ChoicePresentation.Picker,
            ChoicePresentation.of(listOf("Bottom edge", "Elevation", "Nothing at all"))
        )
        assertTrue(ChoicePresentation.entries.size == 2)
    }
}
