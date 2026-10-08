// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
    id("uk.android.compose")
}

android {
    namespace = "com.qtekfun.ultimatekeys.screenshots"
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Golden images live in the repository so a change of look is a reviewable diff. The Roborazzi
// Gradle plugin is not used (it does not support this AGP); its libraries read these properties.
//   ./gradlew :screenshots:testDebugUnitTest                           verifies against the goldens
//   ./gradlew :screenshots:testDebugUnitTest -Proborazzi.test.record=true  rewrites them
val recordGoldens = providers.gradleProperty(
    "roborazzi.test.record"
).map(String::toBoolean).orElse(false)
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", recordGoldens.get())
    systemProperty("roborazzi.test.verify", !recordGoldens.get())
    systemProperty("snapshot.dir", layout.projectDirectory.dir("src/test/snapshots").asFile.path)
    outputs.upToDateWhen { false }
}

dependencies {
    testImplementation(projects.ime)
    testImplementation(projects.style)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // The screenshot tests are JUnit 4 (Robolectric); the other modules use Jupiter.
    testRuntimeOnly(libs.junit.vintage.engine)
}
