// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
    id("uk.android.native")
}

android {
    namespace = "com.qtekfun.ultimatekeys.voice"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
