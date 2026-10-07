// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
}

android {
    namespace = "com.qtekfun.ultimatekeys.ime"
}

dependencies {
    api(projects.core)
    api(projects.layouts)
}
