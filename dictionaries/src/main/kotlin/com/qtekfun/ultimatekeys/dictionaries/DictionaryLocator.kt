// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import java.io.File
import java.util.Locale

/** Gives the engine the path of the installed dictionary for a locale. Matching is by language (`es_MX` -> `es`). */
class DictionaryLocator internal constructor(
    private val index: DictionaryIndex,
    private val dir: File
) {
    /** Dictionary data version; changes when bundled sources change. */
    val version: String get() = index.version

    /** Format of the files returned by [fileFor] (for example `aosp-combined-gz`). */
    val format: String get() = index.format

    val languages: List<String> get() = index.assets.map { it.language }

    fun fileFor(locale: Locale): File? =
        index.assetFor(locale.language)?.let { File(dir, it.fileName) }?.takeIf { it.isFile }
}
