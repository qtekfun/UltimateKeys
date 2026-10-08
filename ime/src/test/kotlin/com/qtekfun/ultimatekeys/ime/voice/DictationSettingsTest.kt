// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.voice

import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.voice.DictationLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DictationSettingsTest {
    @Test
    fun `defaults mean automatic language and the default silence timeout`() {
        val config = KeyboardSettings().toDictationConfig()
        assertEquals(DictationLanguage.AUTO, config.language)
        assertEquals(1500, config.vad.silenceTimeoutMs)
    }

    @Test
    fun `each language choice maps to the engine language`() {
        fun language(code: String) =
            KeyboardSettings(dictationLanguage = code).toDictationConfig().language
        assertEquals(DictationLanguage.SPANISH, language("es"))
        assertEquals(DictationLanguage.ENGLISH, language("en"))
        assertEquals(DictationLanguage.AUTO, language("auto"))
        assertEquals(DictationLanguage.AUTO, language("whatever"))
    }

    @Test
    fun `the silence timeout reaches the voice detector and is kept in range`() {
        assertEquals(
            2500,
            KeyboardSettings(dictationSilenceMs = 2500).toDictationConfig().vad.silenceTimeoutMs
        )
        assertEquals(
            500,
            KeyboardSettings(dictationSilenceMs = 10).toDictationConfig().vad.silenceTimeoutMs
        )
    }
}
