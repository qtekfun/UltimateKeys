// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("uk.quality")

        val appVersion = providers.gradleProperty("appVersion").get()
        // Release signing from the environment (CI secrets); without it the release APK is unsigned.
        val releaseKeystore: String? = System.getenv("UK_KEYSTORE_FILE")

        extensions.configure(ApplicationExtension::class.java) {
            configureAndroidCommon(this)
            defaultConfig {
                applicationId = "com.qtekfun.ultimatekeys"
                targetSdk = TARGET_SDK
                versionCode = versionCodeOf(appVersion)
                versionName = appVersion
            }
            flavorDimensions += "distribution"
            productFlavors {
                create("full") { dimension = "distribution" }
                create("lite") { dimension = "distribution" }
            }
            signingConfigs {
                if (releaseKeystore != null) {
                    create("release") {
                        storeFile = file(releaseKeystore)
                        storePassword = System.getenv("UK_KEYSTORE_PASSWORD")
                        keyAlias = System.getenv("UK_KEY_ALIAS")
                        keyPassword = System.getenv("UK_KEY_PASSWORD")
                    }
                }
            }
            // Reproducible builds (F-Droid): no Google-encrypted dependency blob in the APK.
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
            buildTypes {
                release {
                    signingConfig = signingConfigs.findByName("release")
                    // The git commit is not part of the APK: a build from a source tarball must match.
                    vcsInfo.include = false
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
            }
        }

        val verifyAll = tasks.register("verifyFullHasNoInternet") {
            group = "verification"
            description = "Fails if any full-flavor merged manifest declares network permissions."
        }
        extensions.getByType(ApplicationAndroidComponentsExtension::class.java).onVariants { variant ->
            if (variant.productFlavors.any { it.second == "full" }) {
                val name = variant.name.replaceFirstChar { it.uppercase() }
                val task = tasks.register("verify${name}HasNoInternet", VerifyNoInternetTask::class.java) {
                    manifests.from(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                }
                verifyAll.configure { dependsOn(task) }
            }
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyAll) }
    }
}
