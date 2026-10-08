// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A licence text bundled as an asset. */
data class LicenseInfo(val id: String, val name: String, val asset: String)

/**
 * One component of the licences screen: our own code, third-party code or data, a font, an icon
 * set or a library. [licenseAsset] overrides the shared text when the licence names its holder
 * (the fonts). [thirdPartyRows] are the cells of `docs/THIRD_PARTY.md` this entry covers and
 * [modules] the Gradle coordinates (`group:name`) of the libraries it stands for.
 */
data class LicenseComponent(
    val name: String,
    val category: String,
    val use: String,
    val license: String,
    val copyright: String,
    val url: String,
    val licenseAsset: String?,
    val thirdPartyRows: List<String>,
    val modules: List<String>
)

/** The data behind the "Open-source licenses" screen, read from `assets/licenses/components.json`. */
class LicenseCatalog(val licenses: List<LicenseInfo>, val components: List<LicenseComponent>) {
    private val byId = licenses.associateBy { it.id }

    fun license(id: String): LicenseInfo? = byId[id]

    /** The asset to show for [component]: its own text, or the shared text of its licence. */
    fun assetFor(component: LicenseComponent): String? =
        component.licenseAsset ?: byId[component.license]?.asset

    /** Components grouped for display, in the order [CATEGORY_ORDER] gives. */
    fun grouped(): List<Pair<String, List<LicenseComponent>>> =
        components.groupBy { it.category }.toList().sortedBy { (category, _) -> rank(category) }

    private fun rank(category: String): Int {
        val index = CATEGORY_ORDER.indexOf(category)
        return if (index < 0) CATEGORY_ORDER.size else index
    }

    companion object {
        const val ASSET = "licenses/components.json"
        val CATEGORY_ORDER = listOf("app", "code", "data", "fonts", "icons", "libraries", "build")

        fun parse(json: String): LicenseCatalog {
            val root = Json.parseToJsonElement(json).jsonObject
            val licenses = root.array("licenses").map {
                val o = it.jsonObject
                LicenseInfo(o.text("id"), o.text("name"), o.text("asset"))
            }
            val components = root.array("components").map {
                val o = it.jsonObject
                LicenseComponent(
                    name = o.text("name"),
                    category = o.text("category"),
                    use = o.text("use"),
                    license = o.text("license"),
                    copyright = o.text("copyright"),
                    url = o.text("url"),
                    licenseAsset = o["licenseAsset"]?.jsonPrimitive?.contentOrNull,
                    thirdPartyRows = o.strings("thirdPartyRows"),
                    modules = o.strings("modules")
                )
            }
            return LicenseCatalog(licenses, components)
        }

        private fun JsonObject.array(key: String): JsonArray = getValue(key).jsonArray

        private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

        private fun JsonObject.strings(key: String): List<String> =
            this[key]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    }
}
