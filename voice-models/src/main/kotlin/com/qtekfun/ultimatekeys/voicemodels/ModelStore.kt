// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voicemodels

import android.annotation.SuppressLint
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** SHA-256 helpers; hex is lowercase. */
object Checksums {
    private const val BUFFER = 64 * 1024

    fun sha256(file: File): String = file.inputStream().use(::sha256)

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return hex(digest.digest())
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

/** The bytes of a model did not hash to the pinned SHA-256. */
class ChecksumMismatchException(val expected: String, val actual: String) :
    IOException("Checksum mismatch: expected $expected but got $actual")

/** A model file on disk. [spec] is set when it is one of the catalog's models. */
data class InstalledModel(val id: String, val file: File, val bytes: Long, val spec: ModelSpec?)

/**
 * The app-private model directory (`<root>/models`). Everything that becomes a model passes
 * through a staging file, is checked, and is moved into place atomically, so a model file that
 * exists is always complete.
 */
class ModelStore(root: File, private val catalog: ModelCatalog) {
    val directory = File(root, "models")
    private val staging = File(directory, STAGING)

    fun fileFor(spec: ModelSpec): File = File(directory, spec.file)

    /** A catalog model counts as installed when its file has the pinned size (it was verified on the way in). */
    fun isInstalled(spec: ModelSpec): Boolean = fileFor(spec).let {
        it.isFile &&
            it.length() == spec.bytes
    }

    /** Models on disk: catalog ones first (catalog order), then any other `.bin` by name. */
    fun installed(): List<InstalledModel> {
        val files = directory.listFiles { f -> f.isFile && f.extension == "bin" }.orEmpty()
        val known = catalog.models.mapNotNull { spec ->
            files.firstOrNull { it.name == spec.file && it.length() == spec.bytes }
                ?.let { InstalledModel(spec.id, it, it.length(), spec) }
        }
        val knownFiles = known.map { it.file.name }.toSet()
        val others = files.filter { it.name !in knownFiles && catalog.byFile(it.name) == null }
            .sortedBy { it.name }
            .map { InstalledModel(it.nameWithoutExtension, it, it.length(), null) }
        return known + others
    }

    fun delete(model: InstalledModel): Boolean = model.file.delete()

    /** A fresh staging file name; the directory is created on demand. */
    fun stagingFile(name: String): File {
        check(staging.isDirectory || staging.mkdirs()) { "Cannot create $staging" }
        return File(staging, name)
    }

    /** The resumable partial download of [spec]. */
    fun partialFile(spec: ModelSpec): File = stagingFile("${spec.file}.part")

    fun deletePartial(spec: ModelSpec) {
        partialFile(spec).delete()
    }

    // Only compared with the size still needed; cache that Android could clear is not counted on.
    @SuppressLint("UsableSpace")
    fun freeBytes(): Long {
        directory.mkdirs()
        return directory.usableSpace
    }

    /**
     * Copies [input] into staging while hashing, then moves it into place if the hash matches
     * [spec]. Progress is reported in bytes copied.
     */
    fun install(spec: ModelSpec, input: InputStream, onProgress: (Long) -> Unit = {}): File {
        val part = stagingFile("${spec.file}.install")
        try {
            val actual = part.outputStream().use { copyAndHash(input, it, onProgress) }
            if (actual != spec.sha256) throw ChecksumMismatchException(spec.sha256, actual)
            return moveIn(part, spec.file)
        } finally {
            part.delete()
        }
    }

    private fun copyAndHash(
        input: InputStream,
        out: OutputStream,
        onProgress: (Long) -> Unit
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            out.write(buffer, 0, read)
            total += read
            onProgress(total)
        }
        return Checksums.hex(digest.digest())
    }

    /** Hashes a finished file in staging and moves it into place; deletes it when the hash is wrong. */
    fun commit(spec: ModelSpec, staged: File): File {
        val actual = Checksums.sha256(staged)
        if (actual != spec.sha256) {
            staged.delete()
            throw ChecksumMismatchException(spec.sha256, actual)
        }
        return moveIn(staged, spec.file)
    }

    /** Moves an already-checked staging file to [name] in the model directory. */
    fun moveIn(staged: File, name: String): File {
        directory.mkdirs()
        val target = File(directory, name)
        try {
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    /** Removes leftovers of interrupted installs and imports (never the resumable downloads). */
    fun cleanStaging() {
        staging.listFiles { f -> f.name.endsWith(".install") || f.name.endsWith(".import") }
            ?.forEach { it.delete() }
    }

    private companion object {
        const val STAGING = ".staging"
        const val COPY_BUFFER = 64 * 1024
    }
}
