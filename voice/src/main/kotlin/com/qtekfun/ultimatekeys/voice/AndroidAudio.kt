// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** Whether the app may use the microphone right now. */
fun interface MicrophonePermission {
    fun isGranted(): Boolean

    companion object {
        fun of(context: Context): MicrophonePermission = MicrophonePermission {
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}

/** The microphone as 16 kHz mono float samples, kept in memory only. */
@SuppressLint("MissingPermission") // The caller checks MicrophonePermission before creating it.
class MicrophoneSource : AudioSource {
    private var record: AudioRecord? = null
    private var asFloat = true

    override fun start() {
        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferBytes = maxOf(minBytes, SAMPLE_RATE_HZ * Float.SIZE_BYTES / 2)
        val opened = open(AudioFormat.ENCODING_PCM_FLOAT, bufferBytes)
            ?: open(AudioFormat.ENCODING_PCM_16BIT, bufferBytes / 2).also { asFloat = false }
            ?: throw AudioCaptureException("The microphone cannot be opened")
        record = opened
        try {
            opened.startRecording()
        } catch (e: IllegalStateException) {
            opened.release()
            record = null
            throw AudioCaptureException("The microphone cannot be started", e)
        }
        if (opened.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            opened.release()
            record = null
            throw AudioCaptureException("The microphone is in use")
        }
    }

    private fun open(encoding: Int, bufferBytes: Int): AudioRecord? {
        val candidate = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (_: UnsupportedOperationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (candidate.state == AudioRecord.STATE_INITIALIZED) return candidate
        candidate.release()
        return null
    }

    override fun read(buffer: FloatArray): Int {
        val source = record ?: return -1
        if (asFloat) return source.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
        val shorts = ShortArray(buffer.size)
        val count = source.read(shorts, 0, shorts.size, AudioRecord.READ_BLOCKING)
        for (i in 0 until maxOf(count, 0)) buffer[i] = shorts[i] / PCM16_SCALE
        return count
    }

    override fun stop() {
        val source = record ?: return
        record = null
        try {
            source.stop()
        } catch (_: IllegalStateException) {
            // Already stopped by the system.
        }
        source.release()
    }

    private companion object {
        const val PCM16_SCALE = 32768f
    }
}
