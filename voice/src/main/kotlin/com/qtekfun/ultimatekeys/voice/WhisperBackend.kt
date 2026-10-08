// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

/** The native calls [WhisperTranscriber] needs, so that its logic can be tested without them. */
internal interface WhisperBackend {
    /** The handle of the loaded model, or 0 when loading failed. */
    fun load(path: String): Long

    fun free(handle: Long)

    fun abort(handle: Long)

    /** `[language, text]`, or null when inference failed or was aborted. */
    fun transcribe(
        handle: Long,
        audio: FloatArray,
        languages: List<String>,
        threads: Int
    ): Pair<String, String>?

    fun pinCurrentThread(cpus: IntArray): Boolean
}

/** The real thing: whisper.cpp through JNI. Creating it loads the native library. */
internal class JniWhisperBackend : WhisperBackend {
    // Touch the object now so that a missing library fails here, in a place that handles it.
    init {
        NativeWhisper.systemInfo()
    }

    override fun load(path: String): Long = NativeWhisper.load(path)

    override fun free(handle: Long) = NativeWhisper.free(handle)

    override fun abort(handle: Long) = NativeWhisper.abort(handle)

    override fun transcribe(
        handle: Long,
        audio: FloatArray,
        languages: List<String>,
        threads: Int
    ): Pair<String, String>? {
        val result = NativeWhisper.transcribe(handle, audio, languages.toTypedArray(), threads)
        return result?.let { it[0] to it[1] }
    }

    override fun pinCurrentThread(cpus: IntArray): Boolean = NativeWhisper.pinCurrentThread(cpus)
}
