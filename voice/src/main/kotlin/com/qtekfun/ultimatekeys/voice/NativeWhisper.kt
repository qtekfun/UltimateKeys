// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voice

/**
 * Raw JNI surface of `libukwhisper.so` (whisper.cpp plus our glue in `src/main/cpp`). The native
 * names are derived from this class and method names, so renaming anything here breaks the binding.
 * Nothing outside `:voice` uses this object: [JniWhisperBackend] wraps it behind [WhisperBackend].
 *
 * A handle is a native pointer held as `Long`; `0` means "none". None of these calls may run on
 * the main thread: loading reads the whole model and inference takes seconds.
 */
internal object NativeWhisper {
    init {
        System.loadLibrary("ukwhisper")
    }

    /** Loads the model file at [path]; returns the handle, or 0 when it cannot be loaded. */
    @JvmStatic
    external fun load(path: String): Long

    @JvmStatic
    external fun free(handle: Long)

    /** Asks the running [transcribe] of [handle] to stop. The only call allowed from any thread. */
    @JvmStatic
    external fun abort(handle: Long)

    /**
     * Returns `[language, text]`, or null when inference failed or was aborted. One entry in
     * [languages] forces it; several make the model pick the most probable among them.
     */
    @JvmStatic
    external fun transcribe(
        handle: Long,
        audio: FloatArray,
        languages: Array<String>,
        threads: Int
    ): Array<String>?

    /** Restricts the calling thread, and the workers it spawns, to [cpus]. */
    @JvmStatic
    external fun pinCurrentThread(cpus: IntArray): Boolean

    @JvmStatic
    external fun systemInfo(): String
}
