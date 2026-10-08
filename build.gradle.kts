// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    base
    id("uk.root")
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
                    "com.qtekfun.ultimatekeys.ime.surface.KeyIcons",
                    "com.qtekfun.ultimatekeys.ime.surface.FontCatalog",
                    "com.qtekfun.ultimatekeys.ime.surface.AngleGradient",
                    "com.qtekfun.ultimatekeys.ime.surface.ToneSource\$Companion",
                    "com.qtekfun.ultimatekeys.styles.*",
                    "com.qtekfun.ultimatekeys.core.SettingsStoreKt*",
                    // Dictation: JNI glue, Android audio and permission plumbing and the panel's
                    // drawing. Their logic lives in tested classes; the emulator test covers JNI.
                    "com.qtekfun.ultimatekeys.voice.NativeWhisper",
                    "com.qtekfun.ultimatekeys.voice.JniWhisperBackend",
                    "com.qtekfun.ultimatekeys.voice.MicrophoneSource",
                    "com.qtekfun.ultimatekeys.voice.MicrophonePermission\$Companion*",
                    "com.qtekfun.ultimatekeys.voice.MicrophonePermissionFlow",
                    "com.qtekfun.ultimatekeys.voice.RecordAudioPermissionActivity*",
                    "com.qtekfun.ultimatekeys.ime.voice.VoicePanelKt*",
                    "com.qtekfun.ultimatekeys.ime.voice.AndroidDictationActions",
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
    dependsOn("originalityCheck")
}
