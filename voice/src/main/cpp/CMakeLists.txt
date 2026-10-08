# SPDX-FileCopyrightText: 2026 UltimateKeys contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds whisper.cpp (third_party/whisper.cpp, MIT, pinned to a release tag) together with our JNI
# glue into libukwhisper.so. NEON is part of the arm64 baseline and enabled for armeabi-v7a below.

cmake_minimum_required(VERSION 3.22.1)
project(ukwhisper LANGUAGES C CXX ASM)

get_filename_component(UK_REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(UK_WHISPER_DIR "${UK_REPO_ROOT}/third_party/whisper.cpp")

if(NOT EXISTS "${UK_WHISPER_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "third_party/whisper.cpp is empty. Run: git submodule update --init --recursive")
endif()

# Release flags for every ABI, whatever the Gradle build type (inference speed matters in debug too).
# Deterministic output: no absolute paths in debug info or __FILE__, no compiler ident string.
set(UK_COMMON_FLAGS
    -O3
    -ffunction-sections
    -fdata-sections
    -fno-ident
    "-ffile-prefix-map=${UK_REPO_ROOT}=."
    "-ffile-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=build"
    "-ffile-prefix-map=${CMAKE_ANDROID_NDK}=ndk")
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    list(APPEND UK_COMMON_FLAGS -mfpu=neon-vfpv4)
endif()
string(REPLACE ";" " " UK_COMMON_FLAGS_STR "${UK_COMMON_FLAGS}")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${UK_COMMON_FLAGS_STR}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${UK_COMMON_FLAGS_STR}")
set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} ${UK_COMMON_FLAGS_STR}")

# Only the CPU backend: no GPU, no dynamic backends, no OpenMP (ggml has its own thread pool).
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_LTO OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)

add_subdirectory("${UK_WHISPER_DIR}" whisper EXCLUDE_FROM_ALL)

add_library(ukwhisper SHARED uk_whisper_jni.cpp)
target_compile_features(ukwhisper PRIVATE cxx_std_17)
target_compile_options(ukwhisper PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden -Wall -Wextra)
target_include_directories(ukwhisper PRIVATE
    "${UK_WHISPER_DIR}/include"
    "${UK_WHISPER_DIR}/ggml/include")
target_link_libraries(ukwhisper PRIVATE whisper ggml log)

# Drop dead code, hide the static libraries' symbols, no build id (it differs when paths differ),
# and request 16 KB page alignment (Android 15+ devices with 16 KB pages).
target_link_options(ukwhisper PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
    -Wl,--build-id=none
    -Wl,-z,max-page-size=16384)
