// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import android.content.Context
import java.io.File
import java.io.InputStream

/** [AssetSource] over the application's assets. */
class AndroidAssetSource(private val context: Context) : AssetSource {
    override fun open(path: String): InputStream = context.assets.open(path)
}

/**
 * Installs the bundled word lists of [languages] (every one when null) under `filesDir/dictionaries`, each once per
 * pinned version, and returns the locator.
 */
fun Context.installDictionaries(languages: Set<String>? = null): DictionaryLocator =
    DictionaryInstaller(AndroidAssetSource(this), File(filesDir, "dictionaries"))
        .ensureInstalled(languages)

/** Where the binary dictionaries of the engine are built (see [BinaryDictionaries]). */
fun Context.binaryDictionariesDir(): File = File(filesDir, "engine/dictionaries")
