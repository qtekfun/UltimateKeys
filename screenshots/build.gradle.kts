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
    val storeDir = layout.buildDirectory.dir("store").get().asFile
    systemProperty("store.dir", storeDir.path)
    doFirst { storeDir.deleteRecursively() }
    outputs.upToDateWhen { false }
}

// Store images: StoreScreenshotTest renders them into build/store/<locale>/<n>_<scene>.png and this
// task copies them (replacing what was there) into the fastlane metadata that F-Droid reads.
//   ./gradlew :screenshots:updateStoreScreenshots
tasks.register<Sync>("updateStoreScreenshots") {
    group = "documentation"
    description = "Renders the store screenshots and copies them into fastlane/metadata/android."
    dependsOn(tasks.named("testDebugUnitTest"))
    from(layout.buildDirectory.dir("store"))
    into(rootProject.layout.projectDirectory.dir("fastlane/metadata/android"))
    eachFile { path = "$path".replaceFirst("/", "/images/phoneScreenshots/") }
    includeEmptyDirs = false
    // Only the images are managed here: texts and changelogs next to them stay.
    preserve { include("**/*.txt", "**/changelogs/**") }
}

dependencies {
    testImplementation(projects.ime)
    testImplementation(projects.style)
    testImplementation(libs.androidx.compose.foundation)
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
