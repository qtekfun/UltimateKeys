// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import groovy.json.JsonSlurper
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    id("uk.android.application")
    id("uk.android.compose")
}

android {
    namespace = "com.qtekfun.ultimatekeys"
    // The bundled model must stay a plain, uncompressed entry of the APK (no copy at install time,
    // and its bytes are exactly the ones whose SHA-256 is pinned).
    androidResources {
        noCompress += "ggml"
    }
}

dependencies {
    implementation(projects.core)
    implementation(projects.ime)
    implementation(projects.layouts)
    implementation(projects.style)
    implementation(projects.engine)
    implementation(projects.dictionaries)
    implementation(projects.privacy)
    implementation(projects.clipboard)
    implementation(projects.emoji)
    implementation(projects.voice)
    implementation(projects.voiceModels)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.compose.material3)
    // The model downloader exists only in the `lite` flavor; `full` has no network code at all.
    "liteImplementation"(libs.androidx.work.runtime)
    // Reads assets/licenses/components.json (already used by :layouts, no new artifact).
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(projects.dictionaries)
    androidTestImplementation(projects.engine)
    androidTestImplementation(projects.voice)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

/**
 * Materialises the model bundled in the `full` flavor as a generated asset.
 *
 * The model is read from `voice-models/src/main/assets/models.json` (URL pinned to a commit of the
 * upstream repository, SHA-256 and size). Deterministic: the output is a byte-for-byte copy of a
 * file whose SHA-256 is pinned. Offline-friendly: a verified copy in the Gradle user home is used
 * without touching the network. The file is never committed.
 */
abstract class FetchBundledModelTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val catalog: RegularFileProperty

    @get:Input
    abstract val modelId: Property<String>

    /** Download cache, keyed by SHA-256. Not an input: it only changes how, not what, we produce. */
    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(catalog.get().asFile) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val models = root["models"] as List<Map<String, Any?>>
        val model = models.first { it["id"] == modelId.get() }
        val sha = (model["sha256"] as String).lowercase()
        val bytes = (model["bytes"] as Number).toLong()
        val url = model["url"] as String
        val cached = cacheDir.get().asFile.resolve("$sha.bin")
        if (!(cached.isFile && cached.length() == bytes && sha256(cached) == sha)) {
            logger.lifecycle("Downloading the ${modelId.get()} model from $url")
            cached.parentFile.mkdirs()
            val tmp = File.createTempFile("model", ".part", cached.parentFile)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                URI(url).toURL().openStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            out.write(buffer, 0, read)
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                check(actual == sha) {
                    "Checksum mismatch for ${modelId.get()}: expected $sha but got $actual"
                }
                check(tmp.length() == bytes) {
                    "Size mismatch for ${modelId.get()}: expected $bytes"
                }
                Files.move(tmp.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                tmp.delete()
            }
        }
        val out = outputDir.get().asFile.resolve("models")
        out.deleteRecursively()
        check(out.mkdirs()) { "Cannot create $out" }
        Files.copy(cached.toPath(), out.resolve("${modelId.get()}.ggml").toPath())
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val fetchBundledModel = tasks.register<FetchBundledModelTask>("fetchBundledModel") {
    group = "voice"
    description =
        "Fetches the model bundled in the full flavor (SHA-256 verified, cached) into generated assets."
    catalog.set(
        rootProject.layout.projectDirectory.file("voice-models/src/main/assets/models.json")
    )
    modelId.set("base")
    cacheDir.set(gradle.gradleUserHomeDir.resolve("ultimatekeys-models"))
    outputDir.set(layout.buildDirectory.dir("generated/bundled-model/assets"))
}

androidComponents {
    onVariants { variant ->
        if (variant.productFlavors.any { it.second == "full" }) {
            variant.sources.assets?.addGeneratedSourceDirectory(
                fetchBundledModel,
                FetchBundledModelTask::outputDir
            )
        }
    }
}
