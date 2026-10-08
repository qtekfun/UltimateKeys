// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.qtekfun.ultimatekeys.clipboard"
}

ksp {
    // The schema is committed so a change of the tables is a reviewable diff and can be migrated.
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
}
