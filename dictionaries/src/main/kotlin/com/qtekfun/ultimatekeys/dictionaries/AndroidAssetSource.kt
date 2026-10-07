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

/** Installs the bundled dictionaries under `filesDir/dictionaries` (once per data version) and returns the locator. */
fun Context.installDictionaries(): DictionaryLocator =
    DictionaryInstaller(AndroidAssetSource(this), File(filesDir, "dictionaries")).ensureInstalled()
