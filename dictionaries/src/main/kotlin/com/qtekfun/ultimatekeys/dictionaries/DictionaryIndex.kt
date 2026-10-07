// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.dictionaries

import java.io.InputStream
import java.util.Properties

/** A dictionary file shipped in the assets. */
data class DictionaryAsset(val language: String, val fileName: String, val sha256: String)

/**
 * Describes the dictionaries bundled in the assets (`dictionaries/index.properties`, generated at build time).
 * [version] changes whenever any pinned source changes, which triggers a refresh of installed copies.
 */
data class DictionaryIndex(
    val version: String,
    val format: String,
    val assets: List<DictionaryAsset>
) {
    fun assetFor(language: String): DictionaryAsset? =
        assets.firstOrNull { it.language == language }

    companion object {
        const val ASSET_DIR = "dictionaries"
        const val INDEX_FILE = "index.properties"

        fun parse(input: InputStream): DictionaryIndex {
            val props = Properties().also { p -> input.use { p.load(it.reader(Charsets.UTF_8)) } }
            fun required(key: String) =
                requireNotNull(props.getProperty(key)) { "index.properties lacks '$key'" }
            val assets = required("languages").split(',').map {
                it.trim()
            }.filter { it.isNotEmpty() }.map { lang ->
                DictionaryAsset(lang, required("$lang.file"), required("$lang.sha256"))
            }
            return DictionaryIndex(required("version"), required("format"), assets)
        }
    }
}
