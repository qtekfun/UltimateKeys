// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManifestRulesTest {
    private fun manifest(vararg permissions: String) = buildString {
        append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n")
        permissions.forEach { append("    <uses-permission android:name=\"$it\" />\n") }
        append("</manifest>")
    }

    @Test
    fun `a full manifest with the microphone only is clean`() {
        val text = manifest("android.permission.RECORD_AUDIO", "android.permission.VIBRATE")
        assertTrue(ManifestRules.forbiddenForFull(text).isEmpty())
        assertFalse(ManifestRules.declaresInternet(text))
    }

    @Test
    fun `network permissions are found in a full manifest`() {
        val text = manifest(ManifestRules.INTERNET, ManifestRules.NETWORK_STATE)
        assertEquals(
            listOf(ManifestRules.INTERNET, ManifestRules.NETWORK_STATE),
            ManifestRules.forbiddenForFull(text)
        )
        assertEquals(
            listOf(ManifestRules.NETWORK_STATE),
            ManifestRules.forbiddenForFull(manifest(ManifestRules.NETWORK_STATE))
        )
    }

    @Test
    fun `a lite manifest declares internet`() {
        assertTrue(ManifestRules.declaresInternet(manifest("android.permission.RECORD_AUDIO", ManifestRules.INTERNET)))
        assertTrue(
            ManifestRules.declaresInternet(
                """<uses-permission-sdk-23 android:name="android.permission.INTERNET"/>"""
            )
        )
    }

    @Test
    fun `a mention in a comment or in another attribute is not a declaration`() {
        val commented = "<manifest><!-- <uses-permission android:name=\"android.permission.INTERNET\" /> --></manifest>"
        assertFalse(ManifestRules.declaresInternet(commented))
        assertTrue(ManifestRules.forbiddenForFull(commented).isEmpty())
        val other = """<uses-permission android:name="android.permission.INTERNET_EXTRA" />"""
        assertFalse(ManifestRules.declaresInternet(other))
        assertFalse(ManifestRules.declaresInternet("<manifest/>"))
    }
}
