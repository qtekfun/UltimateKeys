// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import java.io.File
import java.util.Locale

/**
 * Gives the engine builder the path of the installed word list for a locale. A locale matches the asset with the same
 * language tag (`en-GB`), otherwise the first asset of the same language (`es_MX` finds `es`, `pt` finds `pt-BR`).
 * Only installed, verified files are returned.
 */
class DictionaryLocator internal constructor(
    private val index: DictionaryIndex,
    private val dir: File
) {
    /** Dictionary data version; changes when bundled sources change. */
    val version: String get() = index.version

    /** Format of the files returned by [fileFor] (for example `aosp-combined-gz`). */
    val format: String get() = index.format

    /** Ids (language tags) of every bundled dictionary, installed or not. */
    val languages: List<String> get() = index.assets.map { it.language }

    /** The asset [locale] resolves to, whether or not it is installed. */
    fun assetFor(locale: Locale): DictionaryAsset? = index.assetFor(locale.toLanguageTag())
        ?: index.assets.firstOrNull { it.language.substringBefore('-') == locale.language }

    fun fileFor(locale: Locale): File? = assetFor(locale)?.let { asset ->
        File(dir, asset.fileName).takeIf {
            it.isFile && markerFor(dir, asset).let { m -> m.isFile && m.readText() == asset.sha256 }
        }
    }

    internal companion object {
        fun markerFor(dir: File, asset: DictionaryAsset) = File(dir, asset.fileName + ".sha256")
    }
}
