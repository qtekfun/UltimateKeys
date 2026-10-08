// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatekeys.voice.AudioSource
import com.qtekfun.ultimatekeys.voice.DictationConfig
import com.qtekfun.ultimatekeys.voice.DictationController
import com.qtekfun.ultimatekeys.voice.DictationLanguage
import com.qtekfun.ultimatekeys.voice.DictationResult
import com.qtekfun.ultimatekeys.voice.DictationState
import com.qtekfun.ultimatekeys.voice.VadConfig
import com.qtekfun.ultimatekeys.voice.WhisperTranscriber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real whisper.cpp through JNI with the `tiny` model and a short English recording. The
 * model and the recording are never committed: CI downloads them (cached by checksum), pushes them
 * into the app's files directory (or `/data/local/tmp/ukwhisper`) and passes
 * `-Pandroid.testInstrumentationRunnerArguments.requireWhisper=true` so a missing file fails there
 * instead of skipping. Locally the test skips.
 *
 * Files expected in `filesDir/whisper-test/`: `model.bin` and `sample.wav` (16 kHz mono PCM16).
 */
@RunWith(AndroidJUnit4::class)
class WhisperJniTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val directory = listOf(
        File(instrumentation.targetContext.filesDir, "whisper-test"),
        File("/data/local/tmp/ukwhisper")
    ).firstOrNull { File(it, "model.bin").isFile && File(it, "sample.wav").isFile }
        ?: File(instrumentation.targetContext.filesDir, "whisper-test")
    private val model = File(directory, "model.bin")
    private val sample = File(directory, "sample.wav")
    private val transcriber = WhisperTranscriber(log = { println("UKVoice $it") })

    @Before
    fun requireFiles() {
        val required = InstrumentationRegistry.getArguments().getString("requireWhisper") == "true"
        val present = model.isFile && sample.isFile
        if (required) {
            assertTrue("CI must provide $model and $sample", present)
        } else {
            assumeTrue("whisper test files not provided (CI only)", present)
        }
    }

    @After
    fun close() = transcriber.close()

    @Test
    fun transcribesTheSampleAndDetectsEnglish() = runBlocking {
        transcriber.load(model.path)
        val result = withTimeout(TIMEOUT_MS) {
            transcriber.transcribe(readWav(sample), DictationLanguage.AUTO)
        }
        assertEquals("en", result.language)
        assertTrue("unexpected text: ${result.text}", result.text.lowercase().contains("country"))
        transcriber.unload()
    }

    @Test
    fun aForcedLanguageIsHonoured() = runBlocking {
        transcriber.load(model.path)
        val result = withTimeout(TIMEOUT_MS) {
            transcriber.transcribe(readWav(sample), DictationLanguage.SPANISH)
        }
        assertEquals("es", result.language)
        transcriber.unload()
    }

    @Test
    fun dictatesEndToEndFromAnAudioSource() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val results = mutableListOf<DictationResult>()
        val controller = DictationController(
            scope = scope,
            transcriber = transcriber,
            microphone = { WavSource(readWav(sample)) },
            models = { model },
            permission = { true },
            config = { DictationConfig(vad = VadConfig(silenceTimeoutMs = 1_000)) },
            onResult = { results += it }
        )
        controller.start()
        withTimeout(TIMEOUT_MS) {
            controller.state.first { it is DictationState.Listening }
            controller.state.first { it == DictationState.Idle || it is DictationState.Failed }
        }
        scope.cancel()
        assertEquals(DictationState.Idle, controller.state.value)
        assertEquals(1, results.size)
        assertTrue(
            "unexpected text: ${results[0].text}",
            results[0].text.lowercase().contains("country")
        )
    }

    private fun readWav(file: File): FloatArray {
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        var position = RIFF_HEADER
        while (position + CHUNK_HEADER <= bytes.limit()) {
            val id = String(ByteArray(ID_LENGTH) { bytes.get(position + it) })
            val size = bytes.getInt(position + ID_LENGTH)
            if (id == "data") {
                val count = minOf(size, bytes.limit() - position - CHUNK_HEADER) / 2
                return FloatArray(count) {
                    bytes.getShort(position + CHUNK_HEADER + it * 2) /
                        PCM16_SCALE
                }
            }
            position += CHUNK_HEADER + size + (size and 1)
        }
        error("no data chunk in $file")
    }

    /** Plays [audio] like a microphone would and then keeps delivering silence. */
    private class WavSource(private val audio: FloatArray) : AudioSource {
        private var position = 0

        override fun start() = Unit

        override fun read(buffer: FloatArray): Int {
            val count = minOf(buffer.size, maxOf(audio.size - position, 0))
            // Past the end there is nothing to copy (copyInto rejects a start beyond the array).
            if (count > 0) audio.copyInto(buffer, 0, position, position + count)
            buffer.fill(0f, count, buffer.size)
            position += buffer.size
            // A real microphone delivers in real time; pace it so the VAD sees speech and silence.
            Thread.sleep(PACE_MS)
            return buffer.size
        }

        override fun stop() = Unit

        private companion object {
            const val PACE_MS = 5L
        }
    }

    private companion object {
        const val TIMEOUT_MS = 180_000L
        const val RIFF_HEADER = 12
        const val CHUNK_HEADER = 8
        const val ID_LENGTH = 4
        const val PCM16_SCALE = 32768f
    }
}
