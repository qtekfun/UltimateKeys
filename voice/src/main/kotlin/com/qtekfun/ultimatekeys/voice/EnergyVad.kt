// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import kotlin.math.log10

/** Timing of the automatic stop. */
data class VadConfig(
    /** Silence after speech that ends the recording. */
    val silenceTimeoutMs: Int = DEFAULT_SILENCE_MS,
    /** Without any speech for this long the recording is abandoned. */
    val noSpeechTimeoutMs: Int = DEFAULT_NO_SPEECH_MS,
    /** Speech shorter than this in total is not speech (clicks, a cough). */
    val minSpeechMs: Int = DEFAULT_MIN_SPEECH_MS
) {
    fun sanitized() = copy(
        silenceTimeoutMs = silenceTimeoutMs.coerceIn(SILENCE_RANGE),
        noSpeechTimeoutMs = noSpeechTimeoutMs.coerceIn(NO_SPEECH_RANGE),
        minSpeechMs = minSpeechMs.coerceIn(MIN_SPEECH_RANGE)
    )

    companion object {
        const val DEFAULT_SILENCE_MS = 1_500
        const val DEFAULT_NO_SPEECH_MS = 8_000
        const val DEFAULT_MIN_SPEECH_MS = 150
        val SILENCE_RANGE = 500..5_000
        val NO_SPEECH_RANGE = 2_000..30_000
        val MIN_SPEECH_RANGE = 50..1_000
    }
}

enum class VadVerdict { CONTINUE, SILENCE_AFTER_SPEECH, NO_SPEECH }

/**
 * Voice activity detection on the energy of consecutive frames. The noise floor is measured over
 * the first moments and then follows quieter surroundings, so a fan or a street does not count as
 * speech, while speech must stand clearly above it.
 */
class EnergyVad(config: VadConfig = VadConfig()) {
    private val config = config.sanitized()
    private var elapsedMs = 0
    private var speechMs = 0
    private var silenceMs = 0
    private var floorDb = Float.NaN

    /** Speech has been heard (enough of it, see [VadConfig.minSpeechMs]). */
    var heardSpeech = false
        private set

    /** The latest frame was speech. */
    var speaking = false
        private set

    /** Feeds one frame of [durationMs] whose RMS is [rms]. */
    fun feed(rms: Float, durationMs: Int): VadVerdict {
        elapsedMs += durationMs
        val db = decibels(rms)
        if (elapsedMs <= CALIBRATION_MS) {
            floorDb = if (floorDb.isNaN()) db else minOf(floorDb, db)
        }
        val floor = floorDb.coerceAtMost(MAX_FLOOR_DB)
        speaking = db > maxOf(floor + SPEECH_ABOVE_FLOOR_DB, MIN_SPEECH_DB)
        if (!speaking && elapsedMs > CALIBRATION_MS) {
            // Follow the surroundings slowly. Only non-speech frames count, and they stay below the
            // threshold, so the floor cannot be dragged up by speech.
            floorDb = floorDb * FLOOR_KEEP + db * (1f - FLOOR_KEEP)
        }
        if (speaking) {
            speechMs += durationMs
            silenceMs = 0
            if (speechMs >= config.minSpeechMs) heardSpeech = true
        } else {
            silenceMs += durationMs
        }
        return when {
            heardSpeech && silenceMs >= config.silenceTimeoutMs -> VadVerdict.SILENCE_AFTER_SPEECH
            !heardSpeech && elapsedMs >= config.noSpeechTimeoutMs -> VadVerdict.NO_SPEECH
            else -> VadVerdict.CONTINUE
        }
    }

    companion object {
        private const val CALIBRATION_MS = 300
        private const val SPEECH_ABOVE_FLOOR_DB = 10f
        private const val MIN_SPEECH_DB = -48f
        private const val MAX_FLOOR_DB = -40f
        private const val FLOOR_KEEP = 0.95f
        private const val SILENT_DB = -90f
        private const val METER_BOTTOM_DB = -60f
        private const val METER_TOP_DB = -15f
        private const val DB_PER_BEL = 20f

        /** Level in dB relative to full scale. */
        fun decibels(rms: Float): Float =
            if (rms <= 0f) SILENT_DB else (DB_PER_BEL * log10(rms)).coerceAtLeast(SILENT_DB)

        /** The level meter's value, 0..1, for a frame with RMS [rms]. */
        fun meterLevel(rms: Float): Float =
            ((decibels(rms) - METER_BOTTOM_DB) / (METER_TOP_DB - METER_BOTTOM_DB)).coerceIn(0f, 1f)
    }
}
