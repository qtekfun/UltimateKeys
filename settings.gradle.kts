// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "UltimateKeys"

include(
    ":app",
    ":core",
    ":ime",
    ":gesture",
    ":layouts",
    ":style",
    ":screenshots",
    ":ui",
    ":engine",
    ":dictionaries",
    ":privacy",
    ":clipboard",
    ":emoji",
    ":voice",
    ":voice-models",
)
