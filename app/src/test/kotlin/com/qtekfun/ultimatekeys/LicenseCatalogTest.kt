// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Keeps the licences screen honest: its data file must cover what `docs/THIRD_PARTY.md` lists and
 * what the version catalog ships, and every licence text it points at must be bundled.
 */
class LicenseCatalogTest {
    private val appDir = File(checkNotNull(System.getProperty("user.dir")))
    private val root = appDir.parentFile
    private val catalog = LicenseCatalog.parse(
        File(appDir, "src/main/assets/${LicenseCatalog.ASSET}").readText()
    )

    /** Assets of the app and of the modules whose assets are merged into it (the fonts' licences). */
    private fun assetExists(path: String) = listOf(appDir, File(root, "ime")).any {
        File(it, "src/main/assets/$path").isFile
    }

    @Test
    fun `every component is complete and its licence is known`() {
        assertTrue(catalog.components.size > 10)
        catalog.components.forEach { c ->
            assertTrue(
                c.name.isNotBlank() && c.use.isNotBlank() && c.copyright.isNotBlank(),
                c.name
            )
            assertTrue(c.url.startsWith("https://"), "${c.name}: link must be https")
            assertNotNull(catalog.license(c.license), "${c.name}: unknown licence ${c.license}")
            assertTrue(
                c.category in LicenseCatalog.CATEGORY_ORDER,
                "${c.name}: category ${c.category}"
            )
        }
        assertEquals(catalog.components.size, catalog.components.map { it.name }.toSet().size)
    }

    @Test
    fun `every licence text is bundled and really is that licence`() {
        val needles = mapOf(
            "GPL-3.0-or-later" to "GNU GENERAL PUBLIC LICENSE",
            "Apache-2.0" to "Apache License",
            "MIT" to "Permission is hereby granted",
            "OFL-1.1" to "SIL Open Font License",
            "Unicode-3.0" to "UNICODE LICENSE V3"
        )
        assertEquals(needles.keys, catalog.licenses.map { it.id }.toSet())
        catalog.components.forEach { c ->
            val asset = catalog.assetFor(c) ?: error("${c.name}: no licence text")
            assertTrue(assetExists(asset), "${c.name}: missing asset $asset")
            val text = listOf(appDir, File(root, "ime")).map { File(it, "src/main/assets/$asset") }
                .first { it.isFile }.readText()
            assertTrue(
                text.contains(needles.getValue(c.license), ignoreCase = true),
                "${c.name}: $asset"
            )
        }
    }

    @Test
    fun `the main licences the task names are all offered`() {
        val ids = catalog.components.map { it.license }.toSet()
        assertTrue(
            ids.containsAll(
                listOf("GPL-3.0-or-later", "Apache-2.0", "MIT", "OFL-1.1", "Unicode-3.0")
            )
        )
        assertEquals("UltimateKeys", catalog.components.first().name)
    }

    @Test
    fun `the data file covers every row of THIRD_PARTY dot md`() {
        val cells = thirdPartyCells()
        assertTrue(cells.size >= 10, "found only $cells")
        val covered = catalog.components.flatMap { it.thirdPartyRows }.toSet()
        assertEquals(
            emptyList<String>(),
            (cells - covered).toList(),
            "Rows of docs/THIRD_PARTY.md without an entry in assets/licenses/components.json " +
                "(add the component there, with the row text in thirdPartyRows)"
        )
        assertEquals(
            emptyList<String>(),
            (covered - cells).toList(),
            "thirdPartyRows that no longer exist in docs/THIRD_PARTY.md"
        )
    }

    @Test
    fun `the data file covers every shipped library of the version catalog`() {
        val covered = catalog.components.flatMap { it.modules }.toSet()
        val missing = catalogLibraries().filter { coordinate ->
            coordinate !in covered && coordinate !in NOT_SHIPPED_EXACT &&
                NOT_SHIPPED.none { coordinate.startsWith(it) }
        }
        assertEquals(
            emptyList<String>(),
            missing,
            "Libraries of gradle/libs.versions.toml missing from the licences data " +
                "(add them to a component's modules, or to NOT_SHIPPED(_EXACT) if they are test or build only)"
        )
    }

    @Test
    fun `components are grouped in display order and nothing is dropped`() {
        val grouped = catalog.grouped()
        assertEquals(catalog.components.size, grouped.sumOf { it.second.size })
        assertEquals("app", grouped.first().first)
        val order = grouped.map { LicenseCatalog.CATEGORY_ORDER.indexOf(it.first) }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun `an unknown licence has no text and an own text wins`() {
        val c = catalog.components.first { it.licenseAsset != null }
        assertEquals(c.licenseAsset, catalog.assetFor(c))
        assertEquals(null, catalog.assetFor(c.copy(licenseAsset = null, license = "nope")))
        assertEquals(
            catalog.license("MIT")!!.asset,
            catalog.assetFor(c.copy(licenseAsset = null, license = "MIT"))
        )
    }

    /** First cells of the rows of the component table and of the font table. */
    private fun thirdPartyCells(): Set<String> {
        val lines = File(root, "docs/THIRD_PARTY.md").readLines()
        val cells = LinkedHashSet<String>()
        var inTable = false
        for (line in lines) {
            val isRow = line.startsWith("|")
            if (!isRow) {
                inTable = false
                continue
            }
            val first = line.trim('|').split("|").first().trim()
            when {
                first == "Component" || first == "Font" -> inTable = true
                !inTable -> Unit
                first.startsWith("---") -> Unit
                else -> cells += first
            }
        }
        return cells
    }

    /** `group:name` of the `[libraries]` that ship or run in the app (build-logic ones are skipped). */
    private fun catalogLibraries(): List<String> {
        val libraries = ArrayList<String>()
        var inLibraries = false
        for (line in File(root, "gradle/libs.versions.toml").readLines()) {
            when {
                line.startsWith("[libraries]") -> inLibraries = true

                line.startsWith("[plugins]") -> inLibraries = false

                line.startsWith("# Build-logic classpath") -> inLibraries = false

                inLibraries -> {
                    val match = Regex("""group = "([^"]+)", name = "([^"]+)"""").find(line)
                    if (match !=
                        null
                    ) {
                        libraries += "${match.groupValues[1]}:${match.groupValues[2]}"
                    }
                }
            }
        }
        return libraries
    }

    private companion object {
        /** Test and tooling coordinates (prefix match) that are not part of the installed app. */
        val NOT_SHIPPED = listOf(
            "androidx.test",
            "junit:",
            "org.junit",
            "org.robolectric",
            "io.github.takahirom",
            "app.cash.turbine",
            "org.jetbrains.kotlinx:kotlinx-coroutines-test"
        )
        val NOT_SHIPPED_EXACT = setOf(
            "androidx.compose.ui:ui-tooling",
            "androidx.compose.ui:ui-test-junit4",
            "androidx.compose.ui:ui-test-manifest"
        )
    }
}
