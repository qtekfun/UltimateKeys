// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fetches the pinned Unicode emoji list and CLDR annotations, verifies their SHA-256 and writes the
 * compact emoji assets. Deterministic, and offline once the verified files are cached.
 */
abstract class GenerateEmojiDataTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sources: RegularFileProperty

    /** Download cache keyed by SHA-256; not an input because it only changes how, not what, we produce. */
    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val props = Properties().also { p -> sources.get().asFile.inputStream().use(p::load) }
        val out = outputDir.get().asFile.resolve("emoji")
        out.deleteRecursively()
        check(out.mkdirs()) { "Cannot create $out" }

        val emojiTest = fetch(props.getProperty("emojiTest.url"), props.getProperty("emojiTest.sha256"))
        val groups = EmojiDataProcessor.parseEmojiTest(emojiTest.toString(Charsets.UTF_8))
        out.resolve("catalog.txt").writeText(EmojiDataProcessor.renderCatalog(groups))

        val languages = props.getProperty("languages").split(',').map { it.trim() }
        for (language in languages) {
            val url = props.getProperty("cldr.urlTemplate").format(props.getProperty("cldr.commit"), language)
            val json = fetch(url, props.getProperty("$language.sha256")).toString(Charsets.UTF_8)
            val derivedUrl = props.getProperty("cldr.derivedUrlTemplate")
                .format(props.getProperty("cldr.commit"), language)
            val derived = fetch(derivedUrl, props.getProperty("$language.derived.sha256"))
                .toString(Charsets.UTF_8)
            // Base annotations win over derived ones (flags, hair colours and other sequences).
            val annotations = EmojiDataProcessor.parseAnnotations(derived) +
                EmojiDataProcessor.parseAnnotations(json)
            out.resolve("search_$language.tsv").writeText(EmojiDataProcessor.renderSearch(groups, annotations))
        }
        out.resolve("index.properties").writeText(
            "version=${props.getProperty("dataVersion")}\nlanguages=${languages.joinToString(",")}\n"
        )
    }

    private fun fetch(url: String, expected: String): ByteArray {
        val sha = expected.lowercase()
        val cached = cacheDir.get().asFile.resolve("$sha.bin")
        if (cached.isFile) {
            val bytes = cached.readBytes()
            if (sha256(bytes) == sha) return bytes
        }
        logger.lifecycle("Downloading $url")
        val bytes = URI(url).toURL().openStream().use { it.readBytes() }
        val actual = sha256(bytes)
        check(actual == sha) { "Checksum mismatch for $url: expected $sha but got $actual" }
        cached.parentFile.mkdirs()
        val tmp = File.createTempFile("emoji", ".part", cached.parentFile)
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return bytes
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
