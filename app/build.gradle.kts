// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.application")
    id("uk.android.compose")
}

android {
    namespace = "com.qtekfun.ultimatekeys"
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
    implementation(libs.androidx.compose.material3)

    androidTestImplementation(projects.dictionaries)
    androidTestImplementation(projects.engine)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
