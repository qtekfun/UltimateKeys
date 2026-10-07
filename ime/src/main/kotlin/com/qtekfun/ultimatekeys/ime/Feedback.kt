// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.ime

import android.content.Context
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.qtekfun.ultimatekeys.layouts.KeyAction

/** Haptic and sound feedback for key presses, scaled by the user's settings. */
class Feedback(context: Context) {
    private val vibrator: Vibrator = context.getSystemService(
        VibratorManager::class.java
    ).defaultVibrator
    private val audio: AudioManager = context.getSystemService(AudioManager::class.java)

    fun keyDown(action: KeyAction?, hapticIntensity: Int, soundVolume: Int) {
        if (hapticIntensity > 0 && vibrator.hasVibrator()) {
            val amplitude = (hapticIntensity * MAX_AMPLITUDE / PERCENT).coerceIn(1, MAX_AMPLITUDE)
            vibrator.vibrate(VibrationEffect.createOneShot(TICK_MS, amplitude))
        }
        if (soundVolume > 0) {
            val effect = when (action) {
                KeyAction.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyAction.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyAction.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(effect, soundVolume / PERCENT.toFloat())
        }
    }

    private companion object {
        const val TICK_MS = 12L
        const val MAX_AMPLITUDE = 255
        const val PERCENT = 100
    }
}
