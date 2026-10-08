// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import java.io.File

/** One forbidden name found in one file. */
data class OriginalityHit(val path: String, val line: Int, val term: String)

/**
 * Finds names of other keyboard products in our own files (CLAUDE.md, "Originality"). Presets,
 * strings, identifiers, docs and store texts must not refer to them.
 */
object OriginalityScanner {
    /** Whole-word, case-insensitive. The spec documents that forbid them are excluded by the caller. */
    val forbiddenTerms = listOf(
        "gboard", "google keyboard", "swiftkey", "samsung", "ios", "iphone", "apple keyboard",
        "heliboard", "openboard", "florisboard", "anysoftkeyboard", "any soft keyboard",
        "fleksy", "kika", "gingerkeyboard", "go keyboard", "simplekeyboard", "microsoft keyboard"
    )

    private val patterns = forbiddenTerms.map { it to Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE) }

    val textExtensions = setOf(
        "kt", "kts", "java", "xml", "json", "md", "txt", "yml", "yaml", "properties", "toml",
        "cpp", "h", "pro", "ukstyle", "html", "cmake"
    )

    /** Files at any depth with these names are not scanned (they name the products to forbid them). */
    val excludedNames = setOf(
        "SPEC.md", "CLAUDE.md", "PLAN.md", "OriginalityScanner.kt", "OriginalityScannerTest.kt"
    )

    private val excludedDirs = setOf("build", ".git", ".gradle", ".claude", ".cxx", "third_party", "node_modules")

    fun scan(root: File): List<OriginalityHit> = root.walkTopDown()
        .onEnter { it.name !in excludedDirs }
        .filter { it.isFile && it.extension.lowercase() in textExtensions && it.name !in excludedNames }
        .flatMap { scanFile(root, it) }
        .toList()

    fun scanText(path: String, text: String): List<OriginalityHit> =
        text.lineSequence().withIndex().flatMap { (index, line) ->
            patterns.filter { (_, regex) -> regex.containsMatchIn(line) }
                .map { (term, _) -> OriginalityHit(path, index + 1, term) }
        }.toList()

    private fun scanFile(root: File, file: File): Sequence<OriginalityHit> =
        scanText(file.relativeTo(root).path, file.readText()).asSequence()
}
