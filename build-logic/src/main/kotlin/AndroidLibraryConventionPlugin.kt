// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("uk.quality")
        extensions.configure(LibraryExtension::class.java) {
            configureAndroidCommon(this)
            // Libraries only expose what their consumers need to see.
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }
    }
}
