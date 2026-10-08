// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import kotlinx.coroutines.CompletableDeferred

/** A transcriber that answers from a script and records how it was used. */
class FakeTranscriber(
    var answer: (FloatArray, DictationLanguage) -> Transcription = { _, _ ->
        Transcription("hello world", "en")
    }
) : SpeechTranscriber {
    override var loadedModel: String? = null
        private set

    val loads = mutableListOf<String>()
    var unloads = 0
    var transcribed = 0
    var failLoad = false
    var failTranscribe = false

    /** When set, transcribe suspends until this completes (to test cancellation). */
    var gate: CompletableDeferred<Unit>? = null
    var lastSamples: FloatArray? = null

    /** The array the transcriber was given (the caller may wipe it afterwards). */
    var lastSamplesReference: FloatArray? = null
    var lastLanguage: DictationLanguage? = null

    override suspend fun load(modelPath: String) {
        if (failLoad) throw ModelLoadException("fake")
        loads += modelPath
        loadedModel = modelPath
    }

    override suspend fun unload() {
        unloads++
        loadedModel = null
    }

    override suspend fun transcribe(
        samples: FloatArray,
        language: DictationLanguage
    ): Transcription {
        if (loadedModel == null) throw TranscriptionException("no model")
        gate?.await()
        if (failTranscribe) throw TranscriptionException("fake")
        transcribed++
        lastSamples = samples.copyOf()
        lastSamplesReference = samples
        lastLanguage = language
        return answer(samples, language)
    }

    override fun close() = Unit
}

/** An audio source that plays [frames] (one read each) and then repeats [after] forever. */
class ScriptedSource(
    private val frames: List<FloatArray>,
    private val after: FloatArray? = null,
    private val failAtStart: Boolean = false
) : AudioSource {
    var started = false
    var stopped = false
    private var index = 0

    override fun start() {
        if (failAtStart) throw AudioCaptureException("busy")
        started = true
    }

    override fun read(buffer: FloatArray): Int {
        val frame = frames.getOrNull(index++) ?: after ?: return -1
        frame.copyInto(buffer, 0, 0, minOf(frame.size, buffer.size))
        return minOf(frame.size, buffer.size)
    }

    override fun stop() {
        stopped = true
    }
}

/** A constant-amplitude frame of [ms] milliseconds. */
fun frame(amplitude: Float, ms: Int = AudioRecorder.FRAME_MS) =
    FloatArray(AudioPrep.samplesFor(ms)) { amplitude }

val SILENCE_FRAME = frame(0.0005f)
val SPEECH_FRAME = frame(0.1f)

/** [count] frames of [value]. */
fun repeatFrame(value: FloatArray, count: Int) = List(count) { value }
