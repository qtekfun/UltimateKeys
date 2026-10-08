// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.qtekfun.ultimatekeys.style"
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
