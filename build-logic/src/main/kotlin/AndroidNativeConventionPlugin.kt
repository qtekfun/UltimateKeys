// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Pinned NDK/CMake and deterministic flags for modules with C++ code (engine, voice). */
class AndroidNativeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.configure(CommonExtension::class.java) {
            ndkVersion = NDK_VERSION
            defaultConfig.ndk.abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            defaultConfig.externalNativeBuild.cmake.arguments +=
                listOf("-DANDROID_STL=c++_static", "-DCMAKE_BUILD_TYPE=Release")
            externalNativeBuild.cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = CMAKE_VERSION
            }
        }
    }
}
