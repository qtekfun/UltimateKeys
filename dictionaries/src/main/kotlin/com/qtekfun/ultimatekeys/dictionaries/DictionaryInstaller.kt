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
 * Copies bundled dictionaries to a private directory (the app files dir) so the engine's builder can read them by
 * path. Only the languages asked for are copied (the keyboard enables a handful out of the many it bundles). Each
 * file has a marker holding the SHA-256 it was verified against, written last, so a missing, partial or outdated
 * copy is detected and redone and a failed copy never looks usable. Files of languages that left the index are
 * removed.
 */
class DictionaryInstaller(private val assets: AssetSource, private val targetDir: File) {
    /**
     * Installs what is missing or outdated for [languages] (every bundled language when null) and returns the
     * locator for the installed files. Safe to call repeatedly: an up-to-date language costs no copying.
     */
    fun ensureInstalled(languages: Set<String>? = null): DictionaryLocator {
        val index = DictionaryIndex.parse(
            assets.open("${DictionaryIndex.ASSET_DIR}/${DictionaryIndex.INDEX_FILE}")
        )
        if (!targetDir.isDirectory &&
            !targetDir.mkdirs()
        ) {
            throw IOException("Cannot create $targetDir")
        }
        removeStale(index)
        index.assets.filter { languages == null || it.language in languages }
            .filterNot { isInstalled(it) }
            .forEach { install(it) }
        return DictionaryLocator(index, targetDir)
    }

    private fun isInstalled(asset: DictionaryAsset): Boolean =
        File(targetDir, asset.fileName).isFile &&
            DictionaryLocator.markerFor(targetDir, asset).let {
                it.isFile &&
                    it.readText() == asset.sha256
            }

    private fun removeStale(index: DictionaryIndex) {
        val keep = index.assets.flatMap {
            listOf(it.fileName, DictionaryLocator.markerFor(targetDir, it).name)
        }.toSet()
        targetDir.listFiles()?.filter { it.name !in keep }?.forEach { it.deleteRecursively() }
    }

    private fun install(asset: DictionaryAsset) {
        val dest = File(targetDir, asset.fileName)
        val marker = DictionaryLocator.markerFor(targetDir, asset)
        val part = File(targetDir, asset.fileName + ".part")
        marker.delete()
        try {
            copyVerified(asset, part)
            dest.delete()
            if (!part.renameTo(dest)) throw IOException("Cannot move $part to $dest")
            marker.writeText(asset.sha256)
        } finally {
            part.delete()
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

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
    }
}
