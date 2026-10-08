// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Compares the default (English) string resources of a module with a translation: every
 * translatable name must exist in both, and the printf placeholders of a name must agree.
 */
object TranslationScanner {
    /** One problem found in a module's resources. */
    data class Problem(val module: String, val language: String, val name: String, val message: String)

    private val placeholder = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z]")
    private val position = Regex("^%\\d+\\$")

    /** Names of the translatable strings of one `strings.xml`, mapped to their placeholder lists. */
    fun parse(xml: String): Map<String, List<String>> {
        val factory = DocumentBuilderFactory.newInstance()
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val result = LinkedHashMap<String, List<String>>()
        val nodes = document.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            if (element.getAttribute("translatable") == "false") continue
            val name = element.getAttribute("name")
            when (element.tagName) {
                "string" -> result[name] = placeholders(element.textContent)

                "plurals", "string-array" -> {
                    val items = element.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        result["$name[$j]"] = placeholders(items.item(j).textContent)
                    }
                }
            }
        }
        return result
    }

    private fun placeholders(text: String): List<String> = placeholder.findAll(text.replace("%%", ""))
        .map { it.value.replace(position, "%") }
        .sorted()
        .toList()

    fun compare(
        module: String,
        language: String,
        base: Map<String, List<String>>,
        translated: Map<String, List<String>>
    ): List<Problem> {
        val problems = ArrayList<Problem>()
        for ((name, args) in base) {
            val other = translated[name]
            when {
                other == null -> problems += Problem(module, language, name, "missing translation")

                other != args -> problems += Problem(
                    module,
                    language,
                    name,
                    "placeholders differ: $args in the default, $other in the translation"
                )
            }
        }
        for (name in translated.keys - base.keys) {
            problems += Problem(module, language, name, "translated but absent from the default strings")
        }
        return problems
    }

    private fun stringFiles(folder: File): List<File> =
        folder.listFiles { f -> f.isFile && f.name.startsWith("strings") && f.extension == "xml" }
            .orEmpty()
            .sortedBy { it.name }

    /** All the `strings*.xml` files of one `values` folder, merged. */
    private fun parseFolder(folder: File): Map<String, List<String>> =
        stringFiles(folder).fold(LinkedHashMap()) { acc, file -> acc.apply { putAll(parse(file.readText())) } }

    /** Scans every `<module>/src/main/res` below [root] (build output and `third_party` skipped). */
    fun scan(root: File, languages: List<String> = listOf("es")): List<Problem> {
        val problems = ArrayList<Problem>()
        val resDirs = root.walkTopDown()
            .onEnter { it.name != "build" && it.name != "third_party" && it.name != ".git" }
            .filter { it.isDirectory && it.name == "res" && it.parentFile.name == "main" }
            .toList()
        for (res in resDirs) {
            val module = res.relativeTo(root).path.substringBefore("/src")
            val base = parseFolder(File(res, "values"))
            for (language in languages) {
                problems += compare(module, language, base, parseFolder(File(res, "values-$language")))
            }
            // A language nobody checks must not appear unnoticed.
            val known = languages.map { "values-$it" }.toSet()
            res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
                .filter { it.name !in known && stringFiles(it).isNotEmpty() }
                .forEach {
                    problems += Problem(module, it.name.removePrefix("values-"), "-", "language is not checked")
                }
        }
        return problems
    }
}
