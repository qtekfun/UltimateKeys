// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** A source of 16 kHz mono float audio, normally the microphone. */
interface AudioSource : AutoCloseable {
    /** Starts capturing. Throws [AudioCaptureException] when the microphone cannot be opened. */
    fun start()

    /** Blocks until some audio is available, fills [buffer] and returns the count; -1 on error. */
    fun read(buffer: FloatArray): Int

    fun stop()

    override fun close() = stop()
}

fun interface AudioSourceFactory {
    fun create(): AudioSource
}

/** The microphone is unavailable (used by a call, muted by policy) or failed while recording. */
class AudioCaptureException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

enum class RecordingEnd { USER_STOPPED, SILENCE, NO_SPEECH, MAX_LENGTH }

/** Recorded [samples] (only in memory) and why recording ended. */
class Recording(val samples: FloatArray, val end: RecordingEnd, val heardSpeech: Boolean)

/**
 * Reads audio from a source into memory until the person stops, the voice activity detector sees
 * the end of the speech, nothing was said for too long or the length cap is reached. Nothing is
 * written to disk. The loop blocks on the source, so run it on an I/O dispatcher.
 */
class AudioRecorder(
    private val vadConfig: () -> VadConfig = { VadConfig() },
    private val maxRecordingMs: Int = DEFAULT_MAX_MS
) {
    /**
     * Records from [source] (which this call starts and stops). [onFrame] gets the meter level
     * (0..1) and whether speech is heard now, once per frame. [stopRequested] is polled per frame.
     */
    suspend fun record(
        source: AudioSource,
        onFrame: (level: Float, speaking: Boolean) -> Unit,
        stopRequested: () -> Boolean
    ): Recording {
        val vad = EnergyVad(vadConfig())
        val frame = FloatArray(AudioPrep.samplesFor(FRAME_MS))
        var audio = FloatArray(AudioPrep.samplesFor(INITIAL_CAPACITY_MS))
        var size = 0
        var lastSpeechEnd = 0
        val maxSamples = AudioPrep.samplesFor(maxRecordingMs)
        source.start()
        try {
            var end: RecordingEnd? = null
            while (end == null) {
                coroutineContext.ensureActive()
                val count = source.read(frame)
                if (count <
                    0
                ) {
                    throw AudioCaptureException("The microphone stopped delivering audio")
                }
                if (size + count >
                    audio.size
                ) {
                    audio = audio.copyOf(maxOf(audio.size * 2, size + count))
                }
                frame.copyInto(audio, size, 0, count)
                size += count
                val rms = AudioPrep.rms(frame, 0, count)
                val verdict = vad.feed(rms, count * MILLIS_PER_SECOND / SAMPLE_RATE_HZ)
                if (vad.speaking) lastSpeechEnd = size
                onFrame(EnergyVad.meterLevel(rms), vad.speaking)
                end = when {
                    stopRequested() -> RecordingEnd.USER_STOPPED
                    verdict == VadVerdict.SILENCE_AFTER_SPEECH -> RecordingEnd.SILENCE
                    verdict == VadVerdict.NO_SPEECH -> RecordingEnd.NO_SPEECH
                    size >= maxSamples -> RecordingEnd.MAX_LENGTH
                    else -> null
                }
            }
            // Keep a short tail after the last word and drop the rest of the silence.
            val keep = if (vad.heardSpeech) {
                minOf(size, lastSpeechEnd + AudioPrep.samplesFor(TAIL_MS))
            } else {
                size
            }
            return Recording(audio.copyOf(keep), end, vad.heardSpeech)
        } finally {
            audio.fill(0f)
            source.stop()
        }
    }

    companion object {
        const val DEFAULT_MAX_MS = 60_000
        const val FRAME_MS = 30
        private const val INITIAL_CAPACITY_MS = 10_000
        private const val TAIL_MS = 400
        private const val MILLIS_PER_SECOND = 1_000
    }
}
