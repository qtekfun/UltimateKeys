// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The person's dictation choices, read each time a dictation starts. */
data class DictationConfig(
    val language: DictationLanguage = DictationLanguage.AUTO,
    val vad: VadConfig = VadConfig()
)

/** Where the model to dictate with is; a later phase's model manager provides the real answer. */
fun interface ModelSource {
    /** The selected model file, or null when there is none. */
    fun currentModel(): File?
}

/**
 * The first `*.bin` model in [directory], by name; null when there is none. A stand-in until the
 * model manager (a later phase) records which model the person selected.
 */
class DirectoryModelSource(private val directory: File) : ModelSource {
    override fun currentModel(): File? = directory.listFiles { file ->
        file.isFile && file.extension == "bin"
    }?.minByOrNull { it.name }
}

enum class DictationError {
    NO_PERMISSION,
    NO_MODEL,
    MODEL_LOAD_FAILED,
    MIC_UNAVAILABLE,
    NO_SPEECH,
    TRANSCRIPTION_FAILED
}

sealed interface DictationState {
    data object Idle : DictationState

    /** Recording. [level] is the meter value (0..1), [speaking] whether speech is heard now. */
    data class Listening(val level: Float = 0f, val speaking: Boolean = false) : DictationState

    data object Transcribing : DictationState

    data class Failed(val error: DictationError) : DictationState
}

data class DictationResult(val text: String, val language: String)

/**
 * One dictation at a time: checks the permission and the model, records until the person stops or
 * the voice detector hears the end, transcribes and hands the text to [onResult]. Audio exists only
 * in memory and is wiped as soon as it is no longer needed. Call it from the main thread; the heavy
 * work runs on [recordDispatcher] and on the transcriber's own thread.
 */
@Suppress("LongParameterList")
class DictationController(
    private val scope: CoroutineScope,
    private val transcriber: SpeechTranscriber,
    private val microphone: AudioSourceFactory,
    private val models: ModelSource,
    private val permission: MicrophonePermission,
    private val config: () -> DictationConfig,
    private val onResult: (DictationResult) -> Unit,
    private val recordDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** How long the model stays in memory after the last dictation; [NEVER_UNLOAD] keeps it. */
    private val idleUnloadMs: Long = DEFAULT_IDLE_UNLOAD_MS
) {
    private val mutableState = MutableStateFlow<DictationState>(DictationState.Idle)
    val state: StateFlow<DictationState> = mutableState.asStateFlow()

    private val recorder = AudioRecorder(vadConfig = { config().vad })
    private var job: Job? = null
    private var warmJob: Job? = null
    private var idleJob: Job? = null
    private val loadLock = Mutex()

    @Volatile
    private var stopRequested = false

    /** Starts listening, or goes to the error state that says what is missing. */
    fun start() {
        val current = mutableState.value
        if (current is DictationState.Listening || current is DictationState.Transcribing) return
        if (!permission.isGranted()) return fail(DictationError.NO_PERMISSION)
        val model = models.currentModel()
        if (model == null || !model.isFile) return fail(DictationError.NO_MODEL)
        stopRequested = false
        idleJob?.cancel()
        mutableState.value = DictationState.Listening()
        job = scope.launch { session(model) }
    }

    /**
     * Loads the model ahead of the first dictation (while the permission prompt is up, for
     * example), so listening starts without waiting. Does nothing while a dictation runs or when
     * there is no model.
     */
    fun warmUp() {
        val current = mutableState.value
        if (current is DictationState.Listening || current is DictationState.Transcribing) return
        val model = models.currentModel()?.takeIf { it.isFile } ?: return
        idleJob?.cancel()
        warmJob?.cancel()
        warmJob = scope.launch {
            try {
                ensureLoaded(model)
            } catch (_: ModelLoadException) {
                // The real attempt, when the person starts talking, reports the problem.
            }
            scheduleUnload()
        }
    }

    /** The system is short of memory: free the model unless a dictation is using it. */
    fun trimMemory() {
        val current = mutableState.value
        if (current is DictationState.Listening || current is DictationState.Transcribing) return
        warmJob?.cancel()
        idleJob?.cancel()
        scope.launch { unloadNow() }
    }

    /** The keyboard is going away: stop everything and free the model. */
    fun release() {
        job?.cancel()
        warmJob?.cancel()
        idleJob?.cancel()
        mutableState.value = DictationState.Idle
        scope.launch { unloadNow() }
    }

    /** Ends the recording now and transcribes what was said. */
    fun stop() {
        if (mutableState.value is DictationState.Listening) stopRequested = true
    }

    /** Throws the audio away and returns to idle, whatever the state. */
    fun cancel() {
        job?.cancel()
        job = null
        stopRequested = false
        mutableState.value = DictationState.Idle
        scheduleUnload()
    }

    /** Leaves an error state. */
    fun dismiss() {
        if (mutableState.value is DictationState.Failed) mutableState.value = DictationState.Idle
    }

    private fun fail(error: DictationError) {
        mutableState.value = DictationState.Failed(error)
        scheduleUnload()
    }

    /** Frees the model after [idleUnloadMs] without a dictation. */
    private fun scheduleUnload() {
        if (idleUnloadMs == NEVER_UNLOAD) return
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleUnloadMs)
            val current = mutableState.value
            if (current !is DictationState.Listening && current !is DictationState.Transcribing) {
                unloadNow()
            }
        }
    }

    private suspend fun unloadNow() = loadLock.withLock { transcriber.unload() }

    private suspend fun session(model: File) {
        try {
            val result = coroutineScope {
                // The model loads while the person starts talking.
                val loading = async { ensureLoaded(model) }
                val recording = withContext(recordDispatcher) {
                    recorder.record(microphone.create(), ::onFrame) { stopRequested }
                }
                try {
                    transcribe(recording, loading)
                } finally {
                    recording.samples.fill(0f)
                }
            }
            finish(result)
        } catch (e: CancellationException) {
            throw e
        } catch (_: AudioCaptureException) {
            fail(DictationError.MIC_UNAVAILABLE)
        } catch (_: ModelLoadException) {
            fail(DictationError.MODEL_LOAD_FAILED)
        } catch (_: TranscriptionException) {
            fail(DictationError.TRANSCRIPTION_FAILED)
        } catch (
            @Suppress("TooGenericExceptionCaught") _: RuntimeException
        ) {
            // A failure here must end the dictation, never the keyboard.
            fail(DictationError.TRANSCRIPTION_FAILED)
        }
    }

    private suspend fun transcribe(recording: Recording, loading: Deferred<Unit>): Transcription? {
        if (recording.end == RecordingEnd.NO_SPEECH) {
            loading.cancel()
            return null
        }
        mutableState.value = DictationState.Transcribing
        loading.await()
        return transcriber.transcribe(recording.samples, config().language)
    }

    private fun finish(result: Transcription?) {
        val text = result?.let { TranscriptCleaner.clean(it.text) }.orEmpty()
        if (text.isEmpty()) return fail(DictationError.NO_SPEECH)
        // Deliver the text first: whoever sees the state go idle can rely on the result being in.
        onResult(DictationResult(text, result?.language.orEmpty()))
        mutableState.value = DictationState.Idle
        scheduleUnload()
    }

    private suspend fun ensureLoaded(model: File) = loadLock.withLock {
        if (transcriber.loadedModel != model.path) transcriber.load(model.path)
    }

    private fun onFrame(level: Float, speaking: Boolean) {
        mutableState.update {
            if (it is DictationState.Listening) DictationState.Listening(level, speaking) else it
        }
    }
}

/** Keep the model in memory for ever. */
const val NEVER_UNLOAD = Long.MAX_VALUE

/** The model is freed after this long without a dictation (about two minutes). */
const val DEFAULT_IDLE_UNLOAD_MS = 120_000L
