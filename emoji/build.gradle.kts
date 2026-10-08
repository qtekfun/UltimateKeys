// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
}

android {
    namespace = "com.qtekfun.ultimatekeys.emoji"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}

/**
 * Builds the emoji catalogue and the per-language search data from the pinned Unicode and CLDR
 * files (see `sources.properties`). Output goes into generated assets, never into the repository.
 */
val generateEmojiData = tasks.register<GenerateEmojiDataTask>("generateEmojiData") {
    group = "emoji"
    description =
        "Fetches the pinned emoji sources (SHA-256 verified, cached) and writes compact assets."
    sources.set(layout.projectDirectory.file("sources.properties"))
    cacheDir.set(gradle.gradleUserHomeDir.resolve("ultimatekeys-emoji"))
    outputDir.set(layout.buildDirectory.dir("generated/emoji/assets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            generateEmojiData,
            GenerateEmojiDataTask::outputDir
        )
    }
}

// Real-data tests read the generated assets; they run offline once the pinned files are cached.
tasks.withType<Test>().configureEach {
    dependsOn(generateEmojiData)
    systemProperty(
        "emoji.assets",
        layout.buildDirectory.dir("generated/emoji/assets").get().asFile.path
    )
}
