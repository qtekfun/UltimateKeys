# SPDX-FileCopyrightText: 2026 UltimateKeys contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds the vendored AOSP LatinIME native engine (third_party/aosp-latinime, Apache-2.0)
# into libukengine.so. The JNI entry points register against
# com.qtekfun.ultimatekeys.engine.NativeEngine (see third_party/aosp-latinime/MODIFICATIONS.md).

cmake_minimum_required(VERSION 3.22.1)
project(ukengine LANGUAGES CXX)

get_filename_component(UK_REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(UK_LATINIME_DIR "${UK_REPO_ROOT}/third_party/aosp-latinime/native/jni")

# GLOB returns a lexicographically sorted list, so the link order is stable.
file(GLOB_RECURSE UK_LATINIME_SOURCES CONFIGURE_DEPENDS
    "${UK_LATINIME_DIR}/*.cpp")

add_library(ukengine SHARED ${UK_LATINIME_SOURCES})

target_include_directories(ukengine PRIVATE
    "${UK_LATINIME_DIR}"
    "${UK_LATINIME_DIR}/src")

target_compile_features(ukengine PRIVATE cxx_std_17)

# Warnings of the vendored code are not ours to fix (see MODIFICATIONS.md); keep the log readable.
target_compile_options(ukengine PRIVATE
    -Wno-unused-parameter
    -Wno-unused-function
    -Wno-deprecated-declarations
    # Deterministic output: no absolute build paths in debug info or __FILE__.
    "-ffile-prefix-map=${UK_REPO_ROOT}=."
    "-ffile-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=build"
    "-ffile-prefix-map=${CMAKE_ANDROID_NDK}=ndk"
    -fno-ident)

if(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
    # Same hack as the upstream Android.bp for x86.
    target_compile_options(ukengine PRIVATE -mstackrealign)
endif()

target_link_libraries(ukengine PRIVATE log)

# The build id embeds a hash that differs when paths differ; drop it for reproducible builds.
target_link_options(ukengine PRIVATE -Wl,--build-id=none -Wl,-z,max-page-size=16384)
