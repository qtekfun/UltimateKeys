<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Distribution

## Channels

- **GitHub Releases**: signed `full` and `lite` APKs plus `SHA256SUMS.txt` for each tag. Same package id and signing key, so users can update across channels.
- **F-Droid** (planned): the `lite` flavor only. Metadata draft in `fdroid/com.qtekfun.ultimatekeys.yml`.

## Decision: the `full` flavor and F-Droid

The `full` flavor bundles a ~60 MB Whisper model that the build downloads from a pinned URL and verifies by SHA-256. F-Droid builds from source and discourages downloading binary blobs at build time, and a large model makes the APK heavy. Assessment: the model is MIT-licensed data, but fetching it during the build is likely to be rejected. Therefore:

- F-Droid receives `lite` only (the model is downloaded by the user at runtime, or imported from a file).
- `full` is published on GitHub Releases only.

The owner can revisit this when submitting to fdroiddata.

## Reproducible builds

Goal: F-Droid's build of a tag matches the APK published on GitHub (`Binaries`, `AllowedAPKSigningKeys`). Measures: pinned JDK/AGP/NDK/CMake, `vcsInfo.include = false`, no dependency-info blob, deterministic native flags (Phase 2). A CI job comparing two clean builds is added in Phase 2.
