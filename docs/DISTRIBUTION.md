<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Distribution

## Channels

- **GitHub Releases**: signed `full` and `lite` APKs plus `SHA256SUMS.txt` for each tag. Same package id and signing key, so users can update across channels.
- **F-Droid** (planned): the `lite` flavor only. Metadata draft in `fdroid/com.qtekfun.ultimatekeys.yml`.

## Decision: the `full` flavor and F-Droid

The `full` flavor bundles the `base` Whisper model (59 707 625 bytes, SHA-256 pinned in `voice-models/src/main/assets/models.json`,
see `docs/MODELS.md`). The build downloads it once from a Hugging Face URL pinned to a commit, verifies the checksum and
stores it uncompressed in the APK. The release APK sizes are in `docs/PERFORMANCE.md`; in short, `full` is the `lite` APK plus
about 57 MiB, because the model is incompressible.

Assessment for F-Droid, honestly:

- The model is MIT-licensed data (OpenAI's Whisper weights converted by the whisper.cpp project), so the licence is not the
  problem. Whether a binary model file is acceptable as an asset is a judgment for the F-Droid reviewers, and nobody has
  asked them yet; this assessment is a prediction, not a ruling.
- F-Droid builds from source on its own servers. Fetching a binary of this size from a third-party host during the build is
  what its reviewers tend to refuse (unreproducible inputs, an external dependency that can disappear, load on their build
  farm). Pinning the commit and the checksum makes our build deterministic, but it does not change that the build depends on a
  download outside the source tree. Committing the model to the repository is not an option (project rule, and it would make
  every clone 60 MB heavier).
- A 60 MB-larger APK also takes space in every F-Droid mirror and update for every user, including those who never dictate.
- `lite` has none of these issues: the model is downloaded by the user at run time (or imported from a file), the app
  declares INTERNET only for that, and the build needs no model.

Decision:

- F-Droid receives `lite` only. The metadata draft in `fdroid/com.qtekfun.ultimatekeys.yml` already builds the `lite` flavor.
- `full` is published on GitHub Releases only, as the fully air-gapped option (no INTERNET permission, dictation works at
  first start). Both flavors share the application id and the signing key, so a user can move from one to the other as an
  update.
- The owner can revisit this when submitting to fdroiddata: if the reviewers accept a pinned, checksummed model as a build
  input, nothing in the code changes; only the F-Droid metadata (a second build entry for `full`) does.

## Reproducible builds

Goal: F-Droid's build of a tag matches the APK published on GitHub (`Binaries`, `AllowedAPKSigningKeys`). Measures: pinned JDK/AGP/NDK/CMake, `vcsInfo.include = false`, no dependency-info blob, deterministic native flags (Phase 2). A CI job comparing two clean builds is added in Phase 2.
