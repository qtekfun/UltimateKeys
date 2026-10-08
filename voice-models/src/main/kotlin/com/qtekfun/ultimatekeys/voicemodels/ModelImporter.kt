// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voicemodels

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Why a file picked by the person cannot be a dictation model. */
enum class ImportRejection {
    /** Not a ggml file (wrong magic number). */
    NOT_GGML,

    /** A ggml file whose header does not look like a Whisper model. */
    NOT_WHISPER,

    /** Too small to hold a model (or empty). */
    TOO_SMALL,

    /** Larger than any Whisper model; refused before it fills the storage. */
    TOO_LARGE
}

/** What looking at an imported file found. Staged files wait for [ModelImporter.accept] or `discard`. */
sealed interface ImportInspection {
    /** The bytes are exactly one of the catalog's models. */
    data class Recognized(val spec: ModelSpec, val staged: File) : ImportInspection

    /** A plausible Whisper ggml file that is not in the catalog: the person must confirm. */
    data class Unrecognized(val staged: File, val sha256: String, val bytes: Long) :
        ImportInspection

    data class Rejected(val reason: ImportRejection) : ImportInspection
}

/** Header checks on the first bytes of a Whisper ggml model. A sanity check, not a security measure. */
object GgmlHeader {
    /** `ggml` magic as whisper.cpp writes it: the 32-bit value 0x67676d6c, little-endian. */
    private val MAGIC = "lmgg".toByteArray(Charsets.US_ASCII)
    private const val VOCAB_OFFSET = 4
    private const val VOCAB_BYTES = 4
    private const val BYTE_MASK = 0xffL
    private const val BITS_PER_BYTE = 8
    const val SIZE = 12
    private const val VOCAB_MIN = 50_000L
    private const val VOCAB_MAX = 60_000L
    private val VOCAB_RANGE = VOCAB_MIN..VOCAB_MAX

    fun isGgml(head: ByteArray): Boolean =
        head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    /** The Whisper vocabulary (the first header field after the magic) has one of the known sizes. */
    fun isWhisper(head: ByteArray): Boolean {
        if (head.size < SIZE || !isGgml(head)) return false
        val vocab = (0 until VOCAB_BYTES).sumOf {
            (head[VOCAB_OFFSET + it].toLong() and BYTE_MASK) shl (BITS_PER_BYTE * it)
        }
        return vocab in VOCAB_RANGE
    }
}

/** Imports a model file the person picked: stage, hash, sanity-check, and install on acceptance. */
class ModelImporter(
    private val store: ModelStore,
    private val catalog: ModelCatalog,
    private val minBytes: Long = MIN_BYTES,
    private val maxBytes: Long = MAX_BYTES
) {
    /** Copies [input] to staging and reports what it is. Reads at most [MAX_BYTES]. */
    fun inspect(input: InputStream): ImportInspection {
        val staged = store.stagingFile("picked.import")
        staged.delete()
        val head = ByteArray(GgmlHeader.SIZE)
        val headRead = readFully(input, head)
        val problem = headProblem(head.copyOf(headRead))
        if (problem != null) return ImportInspection.Rejected(problem)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(head, 0, headRead)
        val total = try {
            staged.outputStream().use { out ->
                out.write(head, 0, headRead)
                copyLimited(input, out, digest, headRead.toLong())
            }
        } catch (e: IOException) {
            staged.delete()
            throw e
        }
        val rejection = when {
            total > maxBytes -> ImportRejection.TOO_LARGE
            total < minBytes -> ImportRejection.TOO_SMALL
            else -> null
        }
        if (rejection != null) {
            staged.delete()
            return ImportInspection.Rejected(rejection)
        }
        val sha = Checksums.hex(digest.digest())
        val spec = catalog.bySha256(sha)
        return if (spec != null) {
            ImportInspection.Recognized(spec, staged)
        } else {
            ImportInspection.Unrecognized(staged, sha, total)
        }
    }

    /** Copies while hashing; stops as soon as more than [maxBytes] were seen (the total then exceeds it). */
    private fun copyLimited(
        input: InputStream,
        out: OutputStream,
        digest: MessageDigest,
        already: Long
    ): Long {
        var total = already
        val buffer = ByteArray(COPY_BUFFER)
        while (total <= maxBytes) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            digest.update(buffer, 0, read)
            out.write(buffer, 0, read)
        }
        return total
    }

    /** Installs a staged file (after the person confirmed an unrecognized one) and returns the model. */
    fun accept(inspection: ImportInspection): InstalledModel? = when (inspection) {
        is ImportInspection.Recognized -> {
            val file = store.moveIn(inspection.staged, inspection.spec.file)
            InstalledModel(inspection.spec.id, file, file.length(), inspection.spec)
        }

        is ImportInspection.Unrecognized -> {
            val name = "imported-${inspection.sha256.take(SHA_PREFIX)}.bin"
            val file = store.moveIn(inspection.staged, name)
            InstalledModel(file.nameWithoutExtension, file, file.length(), null)
        }

        is ImportInspection.Rejected -> null
    }

    fun discard(inspection: ImportInspection) {
        when (inspection) {
            is ImportInspection.Recognized -> inspection.staged.delete()
            is ImportInspection.Unrecognized -> inspection.staged.delete()
            is ImportInspection.Rejected -> Unit
        }
    }

    private fun readFully(input: InputStream, into: ByteArray): Int {
        var filled = 0
        while (filled < into.size) {
            val read = input.read(into, filled, into.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }

    private fun headProblem(head: ByteArray): ImportRejection? = when {
        head.size < GgmlHeader.SIZE -> ImportRejection.TOO_SMALL
        !GgmlHeader.isGgml(head) -> ImportRejection.NOT_GGML
        !GgmlHeader.isWhisper(head) -> ImportRejection.NOT_WHISPER
        else -> null
    }

    companion object {
        /** Even the `tiny` quantized model is tens of megabytes; this only rules out junk. */
        const val MIN_BYTES = 1_000_000L

        /** The largest Whisper model (unquantized `large`) is under 3.2 GB; nothing above 4 GB is one. */
        const val MAX_BYTES = 4_000_000_000L
        private const val COPY_BUFFER = 64 * 1024
        private const val SHA_PREFIX = 12
    }
}
