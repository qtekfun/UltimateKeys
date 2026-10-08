// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
}

android {
    namespace = "com.qtekfun.ultimatekeys.core"
}

dependencies {
    api(projects.style)
    api(projects.languages)
    api(libs.kotlinx.coroutines.core)
    api(libs.androidx.datastore.preferences)
}
