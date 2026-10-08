// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime.voice

import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.voice.DictationConfig
import com.qtekfun.ultimatekeys.voice.DictationLanguage
import com.qtekfun.ultimatekeys.voice.VadConfig

/** What the person chose in the dictation settings, in the form the dictation engine reads. */
fun KeyboardSettings.toDictationConfig(): DictationConfig = DictationConfig(
    language = when (dictationLanguage) {
        "es" -> DictationLanguage.SPANISH
        "en" -> DictationLanguage.ENGLISH
        else -> DictationLanguage.AUTO
    },
    vad = VadConfig(silenceTimeoutMs = dictationSilenceMs).sanitized()
)
