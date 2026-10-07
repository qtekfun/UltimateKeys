// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

/** whisper.cpp JNI, audio capture and voice activity detection. */
object VoiceModule {
    fun label(prefix: String): String = prefix + "voice"
}
