// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import android.content.Context
import android.graphics.Rect
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.ime.surface.SurfaceSpec
import com.qtekfun.ultimatekeys.layouts.ActionKey
import com.qtekfun.ultimatekeys.layouts.CharKey
import com.qtekfun.ultimatekeys.layouts.KeyAction
import com.qtekfun.ultimatekeys.layouts.LayoutRepository
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Enables the keyboard, focuses a text field and types by tapping the real key positions. */
@RunWith(AndroidJUnit4::class)
class TypingSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context: Context = instrumentation.targetContext

    private val keyboardSelector = By.desc(Pattern.compile("Keyboard|Teclado"))
    private var previousIme = ""
    private val imeId
        get() = "${context.packageName}/.ime.UltimateKeysService"

    @After
    fun restoreKeyboard() {
        // The test runs on a real phone: give the user their own keyboard back.
        if (previousIme.isNotBlank()) device.executeShellCommand("ime set $previousIme")
        device.executeShellCommand("ime disable $imeId")
    }

    @Before
    fun enableKeyboard() {
        previousIme = device.executeShellCommand("settings get secure default_input_method").trim()
        val id = imeId
        device.executeShellCommand("ime enable $id")
        device.executeShellCommand("ime set $id")
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        device.pressHome()
        device.executeShellCommand("am start -n ${context.packageName}/.MainActivity")
        // The try-it field lives on the Setup screen, one tap from the home list.
        device.wait(Until.findObject(By.text(Pattern.compile("Setup|Primeros pasos"))), TIMEOUT)
            ?.click()
        device.wait(Until.hasObject(By.clazz("android.widget.EditText")), TIMEOUT)
        device.findObject(By.clazz("android.widget.EditText")).click()
        assertNotNull(
            "keyboard did not show",
            device.wait(Until.findObject(keyboardSelector), TIMEOUT)
        )
    }

    private fun keyboardBounds(): Rect = device.findObject(keyboardSelector).visibleBounds

    private fun tap(label: String) {
        val layout = LayoutRepository.pages("es_qwerty", numberRow = false).letters
        val bounds = keyboardBounds()
        val density = context.resources.displayMetrics.density
        val geometry = SurfaceSpec.geometry(
            layout,
            bounds.width().toFloat(),
            density,
            100,
            sideMarginDp = KeyboardSettings.DEFAULT_SIDE_MARGIN_DP
        )
        val key = geometry.keys.first { (it.key as? CharKey)?.label == label }
        device.click(
            bounds.left + key.centerX.toInt(),
            bounds.top + ((key.top + key.bottom) / 2).toInt()
        )
    }

    private fun tapAction(action: KeyAction) {
        val layout = LayoutRepository.pages("es_qwerty", numberRow = false).letters
        val bounds = keyboardBounds()
        val geometry = SurfaceSpec.geometry(
            layout,
            bounds.width().toFloat(),
            context.resources.displayMetrics.density,
            100,
            sideMarginDp = KeyboardSettings.DEFAULT_SIDE_MARGIN_DP
        )
        val key = geometry.keys.first { (it.key as? ActionKey)?.action == action }
        device.click(
            bounds.left + key.centerX.toInt(),
            bounds.top + ((key.top + key.bottom) / 2).toInt()
        )
    }

    private fun fieldText(): String =
        device.findObject(By.clazz("android.widget.EditText")).text ?: ""

    @Test
    fun typesLettersSpaceAndDelete() {
        "hola".forEach { tap(it.toString()) }
        tapAction(KeyAction.SPACE)
        tap("n")
        tap("x")
        tapAction(KeyAction.DELETE)
        device.waitForIdle()
        assertEquals("hola n", fieldText())
        assertEquals(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ENTER)
    }

    @Test
    fun shiftCapitalizesTheNextLetter() {
        tapAction(KeyAction.SHIFT)
        tap("q")
        tap("u")
        device.waitForIdle()
        assertEquals("Qu", fieldText())
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
