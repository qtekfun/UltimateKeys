<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0006: Toolchain choices

Status: accepted

## Context

Phase 0 needed concrete versions and tools.

## Decision

AGP 9.4.1 with built-in Kotlin, Kotlin 2.4.20, Gradle 9.8.1, JDK 21, NDK 28.2.13676358, CMake 3.31.6, JUnit 5, detekt 1.23.8, Spotless with ktlint 1.8.0, Kover 0.9.11. clang-format (own C++ glue only) is wired when the first own C++ file lands. ABIs: arm64-v8a, armeabi-v7a, x86_64.

## Consequences

Warnings are errors in Kotlin and lint. Versions mirror UltimateDeck where possible.

## Update: no local emulators

The owner asked that, on the development machine, tests run on the real phone (Pixel 8) and never on a local emulator. Instrumented tests are run locally with `./gradlew connectedFullDebugAndroidTest` on the connected device (the test restores the user's own keyboard afterwards). CI keeps its `emulator-tests` job as in `docs/CI_CD.md`.
