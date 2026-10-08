// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * [SpeechTranscriber] on whisper.cpp. All native work runs on one dedicated thread, which is pinned
 * to the fast cores so that the threads whisper spawns for a decode inherit them. Never touches the
 * main thread and keeps no audio after a call returns.
 *
 * @param log receives timing lines (real-time factor, latency); pass it in debug builds only. It
 *   never receives audio or text.
 */
class WhisperTranscriber internal constructor(
    topologyProvider: () -> CpuTopology,
    private val log: ((String) -> Unit)?,
    private val clock: () -> Long,
    private val backendFactory: () -> WhisperBackend,
    private val executor: ExecutorService
) : SpeechTranscriber {
    constructor(
        log: ((String) -> Unit)? = null
    ) : this(
        { CpuTopology.system() },
        log,
        System::nanoTime,
        ::JniWhisperBackend,
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, THREAD_NAME) }
    )

    // Read on the inference thread when first needed, not on the thread that created us.
    private val topology by lazy(topologyProvider)

    private val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    // Touched on the inference thread only (and in close(), after it has been shut down).
    private var backend: WhisperBackend? = null

    @Volatile
    private var handle = 0L
    private var pinned = false

    @Volatile
    private var model: String? = null

    override val loadedModel: String? get() = model

    override suspend fun load(modelPath: String) = withContext(dispatcher) {
        release()
        val started = clock()
        val native = try {
            backend ?: backendFactory().also { backend = it }
        } catch (e: LinkageError) {
            throw ModelLoadException("The speech engine is not available", e)
        }
        val loaded = native.load(modelPath)
        if (loaded == 0L) throw ModelLoadException("Cannot load the speech model")
        handle = loaded
        model = modelPath
        log?.invoke("model loaded in ${millisSince(started)} ms")
        Unit
    }

    override suspend fun unload() = withContext(dispatcher) { release() }

    override suspend fun transcribe(
        samples: FloatArray,
        language: DictationLanguage
    ): Transcription = coroutineScope {
        val current = handle.takeIf { model != null }
            ?: throw TranscriptionException("No speech model is loaded")
        val inference = async(dispatcher) {
            infer(current, samples, language)
                ?: if (isActive) {
                    throw TranscriptionException("The speech engine failed")
                } else {
                    throw CancellationException("Transcription aborted")
                }
        }
        try {
            inference.await()
        } catch (e: CancellationException) {
            // The native call only ends sooner if told to; the scope waits for it to return.
            backend?.abort(current)
            throw e
        }
    }

    /** The transcription, or null when the engine failed or was aborted. */
    private fun infer(
        current: Long,
        samples: FloatArray,
        language: DictationLanguage
    ): Transcription? {
        val native = backend ?: throw TranscriptionException("No speech model is loaded")
        if (handle != current) throw TranscriptionException("The speech model changed")
        pinOnce(native)
        val audio = AudioPrep.padToMinimum(samples)
        val started = clock()
        val result = native.transcribe(current, audio, language.codes, topology.threads)
            ?: return null
        val elapsedMs = millisSince(started)
        val audioMs = audio.size * MILLIS_PER_SECOND / SAMPLE_RATE_HZ
        log?.invoke(
            "transcribed $audioMs ms of audio in $elapsedMs ms " +
                "(real-time factor ${String.format(
                    Locale.ROOT,
                    "%.2f",
                    elapsedMs.toDouble() / audioMs
                )}), " +
                "threads=${topology.threads}, language=${result.first}"
        )
        return Transcription(text = result.second, language = result.first)
    }

    private fun pinOnce(native: WhisperBackend) {
        if (pinned || topology.bigCores.isEmpty()) return
        pinned = true
        val ok = native.pinCurrentThread(topology.bigCores.toIntArray())
        log?.invoke("pinned to cores ${topology.bigCores}: $ok")
    }

    private fun release() {
        val native = backend
        if (native != null && handle != 0L) native.free(handle)
        handle = 0L
        model = null
    }

    private fun millisSince(start: Long): Long = (clock() - start) / NANOS_PER_MILLI

    /** Frees the model and stops the inference thread. The transcriber cannot be used afterwards. */
    override fun close() {
        if (executor.isShutdown) return
        model = null
        executor.execute { release() }
        executor.shutdown()
    }

    private companion object {
        const val THREAD_NAME = "uk-whisper"
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000
    }
}
