// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
}

android {
    namespace = "com.qtekfun.ultimatekeys.privacy"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}

// Private mode must never leak: its rules are covered completely, lines and branches (SPEC section 12).
kover {
    reports {
        verify {
            rule("Private mode rules are fully covered") {
                minBound(100, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
                minBound(100, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
            }
        }
    }
}
