// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voicemodels

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One downloadable dictation model: where it comes from and what its bytes must hash to. */
@Serializable
data class ModelSpec(
    val id: String,
    val name: String,
    /** File name inside the app's model directory. */
    val file: String,
    val bytes: Long,
    val sha256: String,
    val url: String,
    val license: String
)

/** The upstream repository and the commit every URL in the catalog is pinned to. */
@Serializable
data class CatalogOrigin(val repository: String, val commit: String)

/** The supported models, read from `models.json` (the single source for the app and the build). */
@Serializable
data class ModelCatalog(val schema: Int, val source: CatalogOrigin, val models: List<ModelSpec>) {
    fun find(id: String): ModelSpec? = models.firstOrNull { it.id == id }

    fun bySha256(sha256: String): ModelSpec? = models.firstOrNull {
        it.sha256 == sha256.lowercase()
    }

    fun byFile(file: String): ModelSpec? = models.firstOrNull { it.file == file }

    companion object {
        const val SCHEMA = 1
        private val json = Json { ignoreUnknownKeys = true }
        private val SHA = Regex("[0-9a-f]{64}")
        private val SAFE_NAME = Regex("[A-Za-z0-9._-]+\\.bin")

        /** Parses and validates a catalog; throws [IllegalArgumentException] when it is not usable. */
        fun parse(text: String): ModelCatalog {
            val catalog = try {
                json.decodeFromString<ModelCatalog>(text)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("Unreadable model catalog", e)
            }
            require(catalog.schema == SCHEMA) { "Unsupported catalog schema ${catalog.schema}" }
            require(catalog.models.isNotEmpty()) { "The catalog lists no models" }
            require(
                catalog.models.map {
                    it.id
                }.toSet().size == catalog.models.size
            ) { "Duplicate model id" }
            require(
                catalog.models.map {
                    it.file
                }.toSet().size == catalog.models.size
            ) { "Duplicate model file" }
            catalog.models.forEach(::validate)
            return catalog
        }

        private fun validate(spec: ModelSpec) {
            require(spec.id.isNotBlank()) { "A model has no id" }
            require(SHA.matches(spec.sha256)) {
                "${spec.id}: sha256 must be 64 lowercase hex digits"
            }
            require(spec.bytes > 0) { "${spec.id}: size must be positive" }
            require(SAFE_NAME.matches(spec.file)) { "${spec.id}: unsafe file name" }
            require(spec.url.startsWith("https://")) { "${spec.id}: the URL must be https" }
        }
    }
}
