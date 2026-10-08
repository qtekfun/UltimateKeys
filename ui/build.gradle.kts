// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
    id("uk.android.compose")
}

android {
    namespace = "com.qtekfun.ultimatekeys.ui"
}

// The design system of the app screens: stateless components that take plain values and lambdas, so
// the :screenshots module can render them without the app. No strings live here; callers pass text.
dependencies {
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.savedstate)
}
