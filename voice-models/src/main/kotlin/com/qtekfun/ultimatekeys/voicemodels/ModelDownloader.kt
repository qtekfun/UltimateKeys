// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voicemodels

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The answer to a ranged GET. [rangeStart] is the first byte the body holds, as the server says
 * in `Content-Range` (null when it does not). The network itself lives in the `lite` flavor; this
 * module only knows this seam, so it contains no network code.
 */
class HttpReply(
    val status: Int,
    val rangeStart: Long?,
    val body: InputStream?,
    private val onClose: () -> Unit = {}
) : Closeable {
    override fun close() {
        body?.close()
        onClose()
    }
}

fun interface HttpSource {
    /** GET [url] from byte [rangeStart] on (a plain GET when 0). Throws [IOException] when the network fails. */
    fun open(url: String, rangeStart: Long): HttpReply
}

enum class DownloadFailure {
    /** The connection failed or broke off; the partial file is kept for the next try. */
    NETWORK,

    /** The server answered with an error that may go away (5xx, 408, 429). */
    SERVER_BUSY,

    /** The server refused or does not have the file (other 4xx, odd answers). */
    SERVER_REFUSED,

    /** The file arrived but is not the pinned model. It was deleted. */
    CHECKSUM,

    /** Not enough free space for the rest of the file. */
    NO_SPACE,

    /** Writing the file failed. */
    STORAGE
}

/** What a model download looks like to the person; also what the model manager draws. */
sealed interface DownloadState {
    data object Idle : DownloadState

    /** Waiting for the network (or for Wi-Fi when that is required). */
    data object Queued : DownloadState

    data class Downloading(val bytes: Long, val total: Long) : DownloadState {
        val fraction: Float get() = if (total >
            0
        ) {
            (bytes.toFloat() / total).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    data object Verifying : DownloadState

    data object Completed : DownloadState

    data class Failed(val reason: DownloadFailure) : DownloadState
}

sealed interface DownloadOutcome {
    data object Success : DownloadOutcome

    /** [retryable] failures keep the partial file and are worth another try later. */
    data class Failure(val reason: DownloadFailure, val retryable: Boolean) : DownloadOutcome
}

/**
 * Downloads one model into the store: resumes a partial file with a ranged request, checks the
 * SHA-256 and moves the file into place atomically. Cancelling the coroutine keeps the partial
 * file so the next run resumes; a wrong checksum deletes it.
 */
class ModelDownloader(
    private val store: ModelStore,
    private val http: HttpSource,
    private val freeBytes: () -> Long = store::freeBytes
) {
    suspend fun download(spec: ModelSpec, onState: (DownloadState) -> Unit = {}): DownloadOutcome {
        if (store.isInstalled(spec)) return DownloadOutcome.Success
        val part = store.partialFile(spec)
        var restarts = 0
        while (true) {
            when (val step = transfer(spec, part, onState)) {
                Step.RESTART -> {
                    part.delete()
                    if (++restarts > MAX_RESTARTS) {
                        return DownloadOutcome.Failure(
                            DownloadFailure.SERVER_REFUSED,
                            retryable = false
                        )
                    }
                }

                is Step.Stop -> return step.outcome

                Step.DONE -> return verify(spec, part, onState)
            }
        }
    }

    private suspend fun transfer(
        spec: ModelSpec,
        part: File,
        onState: (DownloadState) -> Unit
    ): Step {
        val have = usablePartial(part, spec)
        return when {
            have >= spec.bytes -> Step.DONE

            freeBytes() < spec.bytes - have + SPACE_MARGIN ->
                Step.Stop(DownloadOutcome.Failure(DownloadFailure.NO_SPACE, retryable = false))

            else -> fetch(spec, part, have, onState)
        }
    }

    private fun usablePartial(part: File, spec: ModelSpec): Long {
        val size = if (part.isFile) part.length() else 0L
        if (size > spec.bytes) {
            part.delete()
            return 0L
        }
        return size
    }

    private fun verify(
        spec: ModelSpec,
        part: File,
        onState: (DownloadState) -> Unit
    ): DownloadOutcome {
        onState(DownloadState.Verifying)
        return try {
            store.commit(spec, part)
            onState(DownloadState.Completed)
            DownloadOutcome.Success
        } catch (_: ChecksumMismatchException) {
            DownloadOutcome.Failure(DownloadFailure.CHECKSUM, retryable = false)
        } catch (_: IOException) {
            DownloadOutcome.Failure(DownloadFailure.STORAGE, retryable = false)
        }
    }

    private suspend fun fetch(
        spec: ModelSpec,
        part: File,
        have: Long,
        onState: (DownloadState) -> Unit
    ): Step {
        onState(DownloadState.Downloading(have, spec.bytes))
        val reply = try {
            http.open(spec.url, have)
        } catch (_: IOException) {
            return Step.Stop(DownloadOutcome.Failure(DownloadFailure.NETWORK, retryable = true))
        }
        return reply.use { classify(spec, part, have, it, onState) }
    }

    private suspend fun classify(
        spec: ModelSpec,
        part: File,
        have: Long,
        reply: HttpReply,
        onState: (DownloadState) -> Unit
    ): Step = when {
        reply.status == STATUS_RANGE_NOT_SATISFIABLE -> Step.RESTART

        reply.status == STATUS_PARTIAL && reply.rangeStart == have && reply.body != null ->
            copy(spec, part, reply.body, have, onState)

        // A 206 that starts somewhere else cannot be appended to what we have.
        reply.status == STATUS_PARTIAL -> Step.RESTART

        // The server ignored the range: it sends the whole file, so start over with it.
        reply.status == STATUS_OK && reply.body != null -> {
            part.delete()
            copy(spec, part, reply.body, 0L, onState)
        }

        isTransient(reply.status) ->
            Step.Stop(DownloadOutcome.Failure(DownloadFailure.SERVER_BUSY, retryable = true))

        else -> Step.Stop(
            DownloadOutcome.Failure(DownloadFailure.SERVER_REFUSED, retryable = false)
        )
    }

    private suspend fun copy(
        spec: ModelSpec,
        part: File,
        body: InputStream,
        start: Long,
        onState: (DownloadState) -> Unit
    ): Step {
        val written = try {
            RandomAccessFile(part, "rw").use { out ->
                out.seek(start)
                pump(spec, body, out, start, onState)
            }
        } catch (_: ReadFailure) {
            return Step.Stop(DownloadOutcome.Failure(DownloadFailure.NETWORK, retryable = true))
        } catch (_: IOException) {
            return Step.Stop(DownloadOutcome.Failure(DownloadFailure.STORAGE, retryable = false))
        }
        return when {
            written > spec.bytes -> {
                part.delete()
                Step.Stop(DownloadOutcome.Failure(DownloadFailure.CHECKSUM, retryable = false))
            }

            written < spec.bytes ->
                Step.Stop(DownloadOutcome.Failure(DownloadFailure.NETWORK, retryable = true))

            else -> Step.DONE
        }
    }

    /** Copies until the body ends or holds more than the model; returns the byte count reached. */
    private suspend fun pump(
        spec: ModelSpec,
        body: InputStream,
        out: RandomAccessFile,
        start: Long,
        onState: (DownloadState) -> Unit
    ): Long {
        var written = start
        var reported = start
        val buffer = ByteArray(BUFFER)
        while (written <= spec.bytes) {
            coroutineContext.ensureActive()
            val read = try {
                body.read(buffer)
            } catch (e: IOException) {
                throw ReadFailure(e)
            }
            if (read < 0) break
            out.write(buffer, 0, read)
            written += read
            if (written - reported >= PROGRESS_STEP) {
                reported = written
                onState(DownloadState.Downloading(written, spec.bytes))
            }
        }
        return written
    }

    /** A failure of the network side of the copy, as opposed to writing the file. */
    private class ReadFailure(cause: IOException) : IOException(cause)

    private fun isTransient(status: Int) =
        status in STATUS_SERVER_ERRORS || status == STATUS_TIMEOUT || status == STATUS_TOO_MANY

    private sealed interface Step {
        data object DONE : Step

        data object RESTART : Step

        data class Stop(val outcome: DownloadOutcome) : Step
    }

    private companion object {
        const val STATUS_OK = 200
        const val STATUS_PARTIAL = 206
        const val STATUS_RANGE_NOT_SATISFIABLE = 416
        const val STATUS_TIMEOUT = 408
        const val STATUS_TOO_MANY = 429
        val STATUS_SERVER_ERRORS = 500..599
        const val BUFFER = 64 * 1024
        const val PROGRESS_STEP = 256 * 1024L
        const val SPACE_MARGIN = 16L * 1024 * 1024
        const val MAX_RESTARTS = 1
    }
}
