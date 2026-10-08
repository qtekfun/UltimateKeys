// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
    id("uk.android.compose")
}

android {
    namespace = "com.qtekfun.ultimatekeys.ime"
}

dependencies {
    api(projects.core)
    api(projects.layouts)
    api(projects.engine)
    api(projects.privacy)
    api(projects.voice)
    implementation(projects.voiceModels)
    api(projects.clipboard)
    api(projects.emoji)
    api(projects.gesture)
    implementation(projects.dictionaries)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    implementation(libs.kotlinx.coroutines.android)
}

// The gesture accuracy and latency tests decode over the real, checksum-verified word lists.
tasks.withType<Test>().configureEach {
    dependsOn(":dictionaries:fetchDictionaries")
    // `-Pgesture.full=true` runs the long accuracy evaluation behind docs/gesture/RESULTS.md.
    systemProperty("gesture.full", providers.gradleProperty("gesture.full").orElse("false").get())
    systemProperty(
        "dictionaries.assets",
        project(
            ":dictionaries"
        ).layout.buildDirectory.dir("generated/dictionaries/assets").get().asFile.path
    )
}

// The part of private mode that lives here (the engine wrapper that stops learning) is covered completely.
kover {
    reports {
        filters {
            includes {
                classes("com.qtekfun.ultimatekeys.ime.suggest.PrivacyGuardedEngine*")
            }
        }
        verify {
            rule("Private mode engine guard is fully covered") {
                minBound(100, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
                minBound(100, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
            }
        }
    }
}
