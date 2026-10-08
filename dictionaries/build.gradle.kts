// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties

plugins {
    id("uk.android.library")
}

android {
    namespace = "com.qtekfun.ultimatekeys.dictionaries"
}

dependencies {
    api(projects.engine)
    testImplementation(projects.languages)
    testImplementation(projects.layouts)
}

/**
 * Materialises the pinned word lists as generated assets.
 *
 * Deterministic: output is a byte-for-byte copy of files whose SHA-256 is pinned in `sources.properties`.
 * Offline-friendly: a verified copy in the Gradle user home cache is used without touching the network.
 */
abstract class FetchDictionariesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sources: RegularFileProperty

    /** Download cache, keyed by SHA-256. Not an input: it only changes how, not what, we produce. */
    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val props = Properties().also { p -> sources.get().asFile.inputStream().use(p::load) }
        val out = outputDir.get().asFile.resolve("dictionaries")
        out.deleteRecursively()
        check(out.mkdirs()) { "Cannot create $out" }
        val languages = props.getProperty("languages").split(',').map { it.trim() }
        val index = StringBuilder()
        index.append("version=").append(props.getProperty("dataVersion")).append('\n')
        index.append("format=").append(props.getProperty("format")).append('\n')
        index.append("languages=").append(languages.joinToString(",")).append('\n')
        for (language in languages) {
            val sha = props.getProperty("$language.sha256").lowercase()
            val bytes = verifiedBytes(props, language, sha)
            // Not ".gz": the Android asset packager would silently unzip such files and break the checksum.
            val name = "$language.wordlist.bin"
            out.resolve(name).writeBytes(bytes)
            index.append("$language.file=").append(name).append('\n')
            index.append("$language.sha256=").append(sha).append('\n')
        }
        out.resolve("index.properties").writeText(index.toString())
    }

    private fun verifiedBytes(props: Properties, language: String, sha: String): ByteArray {
        val cached = cacheDir.get().asFile.resolve("$sha.bin")
        if (cached.isFile) {
            val cachedBytes = cached.readBytes()
            if (sha256(cachedBytes) == sha) return cachedBytes
        }
        val url = props.getProperty("urlTemplate").format(
            props.getProperty("commit"),
            props.getProperty("$language.source")
        )
        logger.lifecycle("Downloading $language dictionary from $url")
        val raw = URI(url).toURL().openStream().use { it.readBytes() }
        // The source serves the blob base64-encoded.
        val bytes = Base64.getMimeDecoder().decode(raw)
        val actual = sha256(bytes)
        check(actual == sha) { "Checksum mismatch for $language: expected $sha but got $actual" }
        cached.parentFile.mkdirs()
        val tmp = File.createTempFile("dict", ".part", cached.parentFile)
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return bytes
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

val fetchDictionaries = tasks.register<FetchDictionariesTask>("fetchDictionaries") {
    group = "dictionaries"
    description = "Fetches the pinned word lists (SHA-256 verified, cached) into generated assets."
    sources.set(layout.projectDirectory.file("sources.properties"))
    cacheDir.set(gradle.gradleUserHomeDir.resolve("ultimatekeys-dictionaries"))
    outputDir.set(layout.buildDirectory.dir("generated/dictionaries/assets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            fetchDictionaries,
            FetchDictionariesTask::outputDir
        )
    }
}

// Real-data tests parse the fetched lists; they run offline once the checksum-pinned files are cached.
tasks.withType<Test>().configureEach {
    dependsOn(fetchDictionaries)
    systemProperty(
        "dictionaries.assets",
        layout.buildDirectory.dir("generated/dictionaries/assets").get().asFile.path
    )
}
