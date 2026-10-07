// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType

/** Formatting (Spotless + ktlint), static analysis (detekt) and coverage (Kover). */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.diffplug.spotless")
        pluginManager.apply("io.gitlab.arturbosch.detekt")
        pluginManager.apply("org.jetbrains.kotlinx.kover")

        val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion
        extensions.configure(SpotlessExtension::class.java) {
            kotlin {
                target("src/**/*.kt")
                ktlint(ktlintVersion)
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint(ktlintVersion)
            }
        }
        extensions.configure(DetektExtension::class.java) {
            buildUponDefaultConfig = true
            allRules = false
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
        }
        tasks.withType<Detekt>().configureEach { jvmTarget = "17" }
        // spotlessCheck and detekt are part of `check` via their plugins.
    }
}
