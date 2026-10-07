// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    base
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlin.serialization) apply false
}

dependencies {
    subprojects.forEach { sub -> kover(sub) }
}

kover {
    reports {
        filters {
            excludes {
                // Generated code, vendored third-party code and pure Compose UI are not measured.
                classes("*.R", "*.R\$*", "*.BuildConfig", "*ComposableSingletons*", "*\$DefaultImpls")
                annotatedBy("androidx.compose.ui.tooling.preview.Preview", "*Generated*")
                classes("com.qtekfun.ultimatekeys.MainActivity*")
            }
        }
        verify {
            rule("Minimum line coverage") {
                minBound(80)
            }
        }
    }
}

// Unit tests of the version-code logic live in the build-logic included build.
tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
    dependsOn("koverVerify")
}
