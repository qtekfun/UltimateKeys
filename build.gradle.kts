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
                // Android glue and pure Compose UI, covered by emulator tests instead.
                classes(
                    "com.qtekfun.ultimatekeys.MainActivity*",
                    "com.qtekfun.ultimatekeys.ImeStatus*",
                    "com.qtekfun.ultimatekeys.ime.UltimateKeysService*",
                    "com.qtekfun.ultimatekeys.ime.AndroidEditorConnection",
                    "com.qtekfun.ultimatekeys.ime.Feedback",
                    "com.qtekfun.ultimatekeys.ime.surface.KeyboardSurface*",
                    "com.qtekfun.ultimatekeys.ime.surface.SurfaceRenderer*",
                    "com.qtekfun.ultimatekeys.ime.surface.KeyboardPreview*",
                    "com.qtekfun.ultimatekeys.ime.surface.FontCatalog",
                    "com.qtekfun.ultimatekeys.ime.surface.AngleGradient",
                    "com.qtekfun.ultimatekeys.ime.surface.ToneSource\$Companion",
                    "com.qtekfun.ultimatekeys.styles.*",
                    "com.qtekfun.ultimatekeys.core.SettingsStoreKt*",
                )
                
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
