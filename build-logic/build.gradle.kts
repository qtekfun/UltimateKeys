// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    `kotlin-dsl`
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.composeGradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "uk.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "uk.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "uk.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidNative") {
            id = "uk.android.native"
            implementationClass = "AndroidNativeConventionPlugin"
        }
        register("quality") {
            id = "uk.quality"
            implementationClass = "QualityConventionPlugin"
        }
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
