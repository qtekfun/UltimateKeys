// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.qtekfun.ultimatekeys.voicemodels"
}

dependencies {
    api(projects.voice)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
}
