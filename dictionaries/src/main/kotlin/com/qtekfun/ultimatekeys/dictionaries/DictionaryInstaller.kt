// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Read access to bundled assets. Backed by `AssetManager` on Android, by a fake in tests. */
fun interface AssetSource {
    /** Opens [path] (relative to the assets root). Throws [IOException] if missing. */
    fun open(path: String): InputStream
}

/**
 * Copies the bundled dictionaries to a private directory (the app files dir) so the native engine can open them
 * by path. A `version` marker file makes the copy happen once per [DictionaryIndex.version]; a failed or partial
 * copy never leaves a usable-looking directory behind.
 */
class DictionaryInstaller(private val assets: AssetSource, private val targetDir: File) {
    /** Installs if needed and returns the locator for the installed files. Safe to call repeatedly. */
    fun ensureInstalled(): DictionaryLocator {
        val index = DictionaryIndex.parse(
            assets.open("${DictionaryIndex.ASSET_DIR}/${DictionaryIndex.INDEX_FILE}")
        )
        val marker = File(targetDir, MARKER_FILE)
        val upToDate = marker.isFile && marker.readText() == index.version &&
            index.assets.all { File(targetDir, it.fileName).isFile }
        if (!upToDate) install(index)
        return DictionaryLocator(index, targetDir)
    }

    private fun install(index: DictionaryIndex) {
        val parent = targetDir.parentFile ?: throw IOException("No parent for $targetDir")
        parent.mkdirs()
        val staging = File(parent, targetDir.name + ".staging")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Cannot create $staging")
        try {
            index.assets.forEach { copyVerified(it, File(staging, it.fileName)) }
            File(staging, MARKER_FILE).writeText(index.version)
            targetDir.deleteRecursively()
            val moved = staging.renameTo(targetDir)
            if (!moved) throw IOException("Cannot move $staging to $targetDir")
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun copyVerified(asset: DictionaryAsset, dest: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        assets.open("${DictionaryIndex.ASSET_DIR}/${asset.fileName}").use { input ->
            dest.outputStream().use { out -> copyHashing(input, out::write, digest) }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != asset.sha256) {
            throw IOException(
                "Checksum mismatch for ${asset.fileName}: expected ${asset.sha256}, got $actual"
            )
        }
    }

    private fun copyHashing(
        input: InputStream,
        write: (ByteArray, Int, Int) -> Unit,
        digest: MessageDigest
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            write(buffer, 0, read)
        }
    }

    companion object {
        const val MARKER_FILE = "version"
        private const val BUFFER_SIZE = 16 * 1024
    }
}
