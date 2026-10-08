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

// SPEC.md section 12: the private-mode rules are covered completely.
kover {
    reports {
        verify {
            rule("Private mode rules: full line coverage") {
                minBound(100)
            }
        }
    }
}
