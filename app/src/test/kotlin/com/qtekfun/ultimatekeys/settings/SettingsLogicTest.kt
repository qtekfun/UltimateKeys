// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import com.qtekfun.ultimatekeys.ImeStatus
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteStackTest {
    private fun stack(vararg routes: SettingsRoute) = RouteStack(routes.toList())

    @Test
    fun `starts at the root and cannot go back`() {
        val start = RouteStack(SettingsRoute.Home)
        assertEquals(SettingsRoute.Home, start.current)
        assertFalse(start.canGoBack)
        assertSame(start, start.back())
    }

    @Test
    fun `opening a screen stacks it and back returns to the previous one`() {
        val typing = RouteStack(SettingsRoute.Home).open(SettingsRoute.Typing)
        assertEquals(SettingsRoute.Typing, typing.current)
        assertTrue(typing.canGoBack)
        assertEquals(SettingsRoute.Home, typing.back().current)
    }

    @Test
    fun `opening a screen already in the stack goes back to it`() {
        val loop = stack(SettingsRoute.Home, SettingsRoute.Typing, SettingsRoute.About)
        assertEquals(
            stack(SettingsRoute.Home, SettingsRoute.Typing),
            loop.open(SettingsRoute.Typing)
        )
        assertEquals(stack(SettingsRoute.Home), loop.open(SettingsRoute.Home))
        assertEquals(loop, loop.open(SettingsRoute.About))
    }

    @Test
    fun `an empty stack is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteStack(emptyList<SettingsRoute>())
        }
    }

    @Test
    fun `survives being saved and restored`() {
        val before = stack(SettingsRoute.Home, SettingsRoute.Clipboard)
        val text = RouteStack.encode(before)
        assertEquals("Home,Clipboard", text)
        assertEquals(before, RouteStack.decode(text, SettingsRoute.entries, SettingsRoute.Home))
    }

    @Test
    fun `a saved stack with unknown or no screens falls back safely`() {
        assertEquals(
            stack(SettingsRoute.Home, SettingsRoute.Feedback),
            RouteStack.decode("Home,Gone,Feedback", SettingsRoute.entries, SettingsRoute.Home)
        )
        assertEquals(
            stack(SettingsRoute.Home),
            RouteStack.decode("Gone,,", SettingsRoute.entries, SettingsRoute.Home)
        )
    }
}

class HomeModelTest {
    private val ready = ImeStatus(enabled = true, selected = true)

    private fun entries(
        settings: KeyboardSettings = KeyboardSettings(),
        status: ImeStatus = ready
    ) = HomeModel.groups(settings, status).flatten()

    @Test
    fun `every area of the app is reachable from the home list exactly once`() {
        val targets = entries().map { it.target }
        assertEquals(HomeTarget.entries.toSet(), targets.toSet())
        assertEquals(targets.size, targets.toSet().size)
    }

    @Test
    fun `targets that live in this activity have a route and the others do not`() {
        val external = HomeTarget.entries.filter { it.route == null }
        assertEquals(setOf(HomeTarget.Appearance, HomeTarget.Dictation), external.toSet())
        // Languages is opened from Typing, not from the home list.
        val subScreens = setOf(SettingsRoute.Home, SettingsRoute.Languages)
        assertEquals(
            SettingsRoute.entries.filter { it !in subScreens }.toSet(),
            HomeTarget.entries.mapNotNull { it.route }.toSet()
        )
    }

    @Test
    fun `setup says whether the keyboard is enabled and selected`() {
        fun summary(status: ImeStatus) = entries(status = status).first {
            it.target ==
                HomeTarget.Setup
        }.summary
        assertEquals(Summary.Ready, summary(ready))
        assertEquals(Summary.NeedsSetup, summary(ImeStatus(enabled = true, selected = false)))
        assertEquals(Summary.NeedsSetup, summary(ImeStatus(enabled = false, selected = false)))
    }

    @Test
    fun `switchable areas show their state`() {
        fun summaries(settings: KeyboardSettings) = entries(settings).associate {
            it.target to
                it.summary
        }
        val off = summaries(
            KeyboardSettings(
                showSuggestions = false,
                gestureTyping = false,
                dictationEnabled = false
            )
        )
        assertEquals(Summary.Off, off[HomeTarget.Suggestions])
        assertEquals(Summary.Off, off[HomeTarget.Gestures])
        assertEquals(Summary.Off, off[HomeTarget.Dictation])
        val on = summaries(KeyboardSettings())
        assertEquals(Summary.On, on[HomeTarget.Suggestions])
        assertEquals(Summary.On, on[HomeTarget.Gestures])
        assertEquals(Summary.On, on[HomeTarget.Dictation])
        assertNull(on[HomeTarget.Typing])
    }

    @Test
    fun `layouts are named by the arrangement of their keys`() {
        assertEquals("QWERTY", LanguagesModel.layoutName("en_qwerty"))
        assertEquals("AZERTY", LanguagesModel.layoutName("fr_azerty"))
        assertEquals("QWERTZ", LanguagesModel.layoutName("de_qwertz"))
        assertEquals("Dvorak", LanguagesModel.layoutName("en_dvorak"))
        assertEquals("Colemak", LanguagesModel.layoutName("en_colemak"))
        assertEquals("ЙЦУКЕН", LanguagesModel.layoutName("ru_jcuken"))
        assertEquals("Q", LanguagesModel.layoutName("tr_q"))
    }
}

class ValueFormatTest {
    @Test
    fun `values carry their own unit`() {
        assertEquals("100%", ValueFormat.percent(100))
        assertEquals("12 dp", ValueFormat.dp(12))
        assertEquals("350 ms", ValueFormat.millis(350))
        assertEquals("90°", ValueFormat.degrees(90))
    }

    @Test
    fun `fractions use the decimal style of the locale`() {
        assertEquals("8.5 dp", ValueFormat.dp(8.5f, Locale.US))
        assertEquals("8,5 dp", ValueFormat.dp(8.5f, Locale.forLanguageTag("es")))
        assertEquals("1.5 s", ValueFormat.seconds(1500, Locale.US))
        assertEquals("0,5 s", ValueFormat.seconds(500, Locale.forLanguageTag("es")))
    }

    @Test
    fun `a continuous drag snaps to the step`() {
        assertEquals(1500, ValueFormat.snap(1540f, 100))
        assertEquals(1600, ValueFormat.snap(1550f, 100))
        assertEquals(500, ValueFormat.snap(500f, 100))
    }
}
