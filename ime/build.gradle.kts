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
    implementation(projects.dictionaries)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    implementation(libs.kotlinx.coroutines.android)
}
