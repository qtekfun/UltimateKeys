// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictationControllerTest {
    private val dir = createTempDirectory("models").toFile()
    private val model = File(dir, "ggml-base.bin").apply { writeText("model") }
    private val transcriber = FakeTranscriber()
    private val results = mutableListOf<DictationResult>()
    private var granted = true
    private var currentModel: File? = model
    private var config = DictationConfig(vad = VadConfig(silenceTimeoutMs = 600))
    private var source: () -> AudioSource = { speechThenSilence() }
    private var sources = 0
    private var idleUnloadMs = NEVER_UNLOAD

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun speechThenSilence() = ScriptedSource(
        repeatFrame(SILENCE_FRAME, 12) + repeatFrame(SPEECH_FRAME, 20),
        after = SILENCE_FRAME
    )

    private fun TestScope.controller() = DictationController(
        scope = this,
        transcriber = transcriber,
        microphone = AudioSourceFactory {
            sources++
            source()
        },
        models = { currentModel },
        permission = { granted },
        config = { config },
        onResult = { results += it },
        recordDispatcher = UnconfinedTestDispatcher(testScheduler),
        idleUnloadMs = idleUnloadMs
    )

    @Test
    fun `dictates from start to inserted text`() = runTest {
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        assertEquals(DictationState.Idle, controller.state.value)
        assertEquals(listOf(DictationResult("hello world", "en")), results)
        assertEquals(listOf(model.path), transcriber.loads)
        assertEquals(DictationLanguage.AUTO, transcriber.lastLanguage)
    }

    @Test
    fun `the model is loaded once across dictations`() = runTest {
        val controller = controller()
        repeat(2) {
            controller.start()
            advanceUntilIdle()
        }
        assertEquals(1, transcriber.loads.size)
        assertEquals(2, results.size)
    }

    @Test
    fun `a forced language reaches the transcriber`() = runTest {
        config = config.copy(language = DictationLanguage.SPANISH)
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        assertEquals(DictationLanguage.SPANISH, transcriber.lastLanguage)
    }

    @Test
    fun `tapping stop transcribes what was recorded`() = runTest {
        source = { ScriptedSource(emptyList(), after = SPEECH_FRAME) }
        val controller = controller()
        // The scripted source never goes quiet: only the stop request ends it.
        controller.start()
        controller.stop()
        advanceUntilIdle()
        assertEquals(1, results.size)
    }

    @Test
    fun `without permission nothing is recorded`() = runTest {
        granted = false
        val controller = controller()
        controller.start()
        assertEquals(DictationState.Failed(DictationError.NO_PERMISSION), controller.state.value)
        assertEquals(0, sources)
        controller.dismiss()
        assertEquals(DictationState.Idle, controller.state.value)
    }

    @Test
    fun `without a model it says so`() = runTest {
        currentModel = null
        val controller = controller()
        controller.start()
        assertEquals(DictationState.Failed(DictationError.NO_MODEL), controller.state.value)
        currentModel = File(dir, "missing.bin")
        controller.dismiss()
        controller.start()
        assertEquals(DictationState.Failed(DictationError.NO_MODEL), controller.state.value)
    }

    @Test
    fun `a model that fails to load is an error`() = runTest {
        transcriber.failLoad = true
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        assertEquals(
            DictationState.Failed(DictationError.MODEL_LOAD_FAILED),
            controller.state.value
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `a busy microphone is an error`() = runTest {
        source = { ScriptedSource(emptyList(), failAtStart = true) }
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        assertEquals(DictationState.Failed(DictationError.MIC_UNAVAILABLE), controller.state.value)
    }

    @Test
    fun `silence only is no speech and skips the model`() = runTest {
        config = DictationConfig(vad = VadConfig(noSpeechTimeoutMs = 2_000))
        source = { ScriptedSource(emptyList(), after = SILENCE_FRAME) }
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        assertEquals(DictationState.Failed(DictationError.NO_SPEECH), controller.state.value)
        assertEquals(0, transcriber.transcribed)
    }

    @Test
    fun `a transcript of only noise is no speech`() = runTest {
        transcriber.answer = { _, _ -> Transcription(" [BLANK_AUDIO]", "en") }
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        assertEquals(DictationState.Failed(DictationError.NO_SPEECH), controller.state.value)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `an engine failure is an error`() = runTest {
        transcriber.failTranscribe = true
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        assertEquals(
            DictationState.Failed(DictationError.TRANSCRIPTION_FAILED),
            controller.state.value
        )
    }

    @Test
    fun `an unexpected failure ends the dictation, not the keyboard`() = runTest {
        transcriber.answer = { _, _ -> error("boom") }
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        assertEquals(
            DictationState.Failed(DictationError.TRANSCRIPTION_FAILED),
            controller.state.value
        )
    }

    @Test
    fun `cancelling while transcribing discards everything`() = runTest {
        val gate = CompletableDeferred<Unit>()
        transcriber.gate = gate
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        assertEquals(DictationState.Transcribing, controller.state.value)
        controller.cancel()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(DictationState.Idle, controller.state.value)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `cancelling while listening stops the microphone`() = runTest {
        config = DictationConfig(vad = VadConfig(noSpeechTimeoutMs = 30_000))
        val controller = controller()
        var reads = 0
        var stopped = false
        source = {
            object : AudioSource {
                override fun start() = Unit

                override fun read(buffer: FloatArray): Int {
                    if (++reads == 3) controller.cancel()
                    SILENCE_FRAME.copyInto(buffer)
                    return buffer.size
                }

                override fun stop() {
                    stopped = true
                }
            }
        }
        controller.start()
        advanceUntilIdle()
        assertEquals(DictationState.Idle, controller.state.value)
        assertTrue(stopped)
        assertEquals(3, reads)
        assertTrue(results.isEmpty())
        assertEquals(0, transcriber.transcribed)
    }

    @Test
    fun `the audio buffer is wiped after the result`() = runTest {
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        assertTrue(transcriber.lastSamples!!.any { it != 0f })
        assertTrue(transcriber.lastSamplesReference!!.all { it == 0f })
    }

    @Test
    fun `start while busy is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        transcriber.gate = gate
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        controller.start()
        assertEquals(1, sources)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `stop outside listening does nothing`() = runTest {
        val controller = controller()
        controller.stop()
        controller.dismiss()
        assertEquals(DictationState.Idle, controller.state.value)
    }

    /** Plays a recording, then silence forever, like the emulator test's file source. */
    private class PlaybackSource(private val audio: FloatArray) : AudioSource {
        private var position = 0

        override fun start() = Unit

        override fun read(buffer: FloatArray): Int {
            val count = minOf(buffer.size, maxOf(audio.size - position, 0))
            if (count > 0) audio.copyInto(buffer, 0, position, position + count)
            buffer.fill(0f, count, buffer.size)
            position += buffer.size
            return buffer.size
        }

        override fun stop() = Unit
    }

    @Test
    fun `a recording that ends in endless silence finishes by itself`() = runTest {
        // Not a multiple of the frame size, so the last frame is partial.
        val audio = FloatArray(AudioPrep.samplesFor(2_000) + 77) { 0.1f }
        source = { PlaybackSource(audio) }
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        assertEquals(DictationState.Idle, controller.state.value)
        assertEquals(1, results.size)
    }

    @Test
    fun `warming up loads the model once and listening reuses it`() = runTest {
        val controller = controller()
        controller.warmUp()
        advanceUntilIdle()
        assertEquals(listOf(model.path), transcriber.loads)
        controller.start()
        advanceUntilIdle()
        assertEquals(1, transcriber.loads.size)
        assertEquals(1, results.size)
    }

    @Test
    fun `warming up without a model does nothing`() = runTest {
        currentModel = null
        val controller = controller()
        controller.warmUp()
        advanceUntilIdle()
        assertTrue(transcriber.loads.isEmpty())
    }

    @Test
    fun `a failing warm-up is silent and the dictation reports it`() = runTest {
        transcriber.failLoad = true
        val controller = controller()
        controller.warmUp()
        advanceUntilIdle()
        assertEquals(DictationState.Idle, controller.state.value)
        controller.start()
        advanceUntilIdle()
        assertEquals(
            DictationState.Failed(DictationError.MODEL_LOAD_FAILED),
            controller.state.value
        )
    }

    @Test
    fun `the model is freed after the idle time and loaded again when needed`() = runTest {
        idleUnloadMs = 60_000L
        val controller = controller()
        controller.start()
        testScheduler.advanceTimeBy(59_000L)
        testScheduler.runCurrent()
        assertEquals(0, transcriber.unloads)
        testScheduler.advanceTimeBy(70_000L)
        testScheduler.runCurrent()
        assertEquals(1, transcriber.unloads)
        assertEquals(null, transcriber.loadedModel)
        controller.start()
        advanceUntilIdle()
        assertEquals(2, transcriber.loads.size)
    }

    @Test
    fun `starting again cancels the pending unload`() = runTest {
        idleUnloadMs = 60_000L
        val controller = controller()
        controller.start()
        testScheduler.advanceTimeBy(40_000L)
        testScheduler.runCurrent()
        controller.start()
        testScheduler.advanceTimeBy(40_000L)
        testScheduler.runCurrent()
        assertEquals(0, transcriber.unloads)
    }

    @Test
    fun `low memory frees the model when idle but never during a dictation`() = runTest {
        source = { ScriptedSource(emptyList(), after = SPEECH_FRAME) }
        val controller = controller()
        controller.start()
        controller.trimMemory()
        testScheduler.runCurrent()
        assertEquals(0, transcriber.unloads)
        controller.stop()
        advanceUntilIdle()
        controller.trimMemory()
        testScheduler.runCurrent()
        assertEquals(1, transcriber.unloads)
    }

    @Test
    fun `releasing the controller cancels a dictation and frees the model`() = runTest {
        source = { ScriptedSource(emptyList(), after = SPEECH_FRAME) }
        val controller = controller()
        controller.start()
        controller.release()
        testScheduler.runCurrent()
        assertEquals(DictationState.Idle, controller.state.value)
        assertEquals(1, transcriber.unloads)
        assertTrue(results.isEmpty())
    }
}
