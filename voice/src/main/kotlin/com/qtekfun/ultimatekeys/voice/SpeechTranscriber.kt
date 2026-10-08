// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

/** Which language the speech is decoded as. */
enum class DictationLanguage(val codes: List<String>) {
    /** Detected per utterance, restricted to Spanish and English. */
    AUTO(listOf("es", "en")),
    SPANISH(listOf("es")),
    ENGLISH(listOf("en"))
}

/** The decoded speech: [text] as the model wrote it and the [language] code it was decoded as. */
data class Transcription(val text: String, val language: String)

/** The model could not be loaded (missing file, corrupt data, native library unavailable). */
class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Inference failed. Cancellation is reported through coroutine cancellation instead. */
class TranscriptionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Turns 16 kHz mono float audio into text. Implementations run inference off the caller's thread, so
 * every call may be made from the main thread. The model is loaded from a file path, which is what
 * the model manager of a later phase hands over.
 */
interface SpeechTranscriber : AutoCloseable {
    /** The path of the loaded model, or null when none is loaded. */
    val loadedModel: String?

    /** Loads the model at [modelPath], replacing the loaded one. Throws [ModelLoadException]. */
    suspend fun load(modelPath: String)

    /** Frees the model. A no-op when none is loaded. */
    suspend fun unload()

    /**
     * Decodes [samples] (16 kHz, mono, -1..1). Cancelling the coroutine aborts the inference.
     * Throws [TranscriptionException] when no model is loaded or inference fails.
     */
    suspend fun transcribe(samples: FloatArray, language: DictationLanguage): Transcription
}

val SpeechTranscriber.isLoaded: Boolean get() = loadedModel != null

/** The sample rate the transcriber and the recorder agree on. */
const val SAMPLE_RATE_HZ = 16_000
