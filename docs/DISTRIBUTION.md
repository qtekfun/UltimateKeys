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

Goal: F-Droid's build of a tag matches the APK published on GitHub (`Binaries`, `AllowedAPKSigningKeys`).

Measures in place:

- Pinned JDK 21, AGP, Gradle (wrapper), NDK `28.2.13676358` (F-Droid name `r28c`) and CMake `3.31.6` (`build-logic`).
- `vcsInfo.include = false` and no dependency-info blob in the APK.
- Native code: `-ffile-prefix-map` for the repository, the build directory and the NDK, `-Wl,--build-id=none`, no timestamps (`engine/src/main/cpp/CMakeLists.txt`, `voice/src/main/cpp/CMakeLists.txt`).
- The version code is derived from `appVersion`, never from a date.
- The `reproducible-build` job of `ci.yml` builds `assembleFullRelease assembleLiteRelease` in two clean checkouts in different directories and fails if the unsigned APKs differ (`cmp`). It runs on every PR.

What the CI job does not prove, and what to check before the first F-Droid submission (human, once `1.0.0` is tagged):

1. Build the tag on a machine that is not the CI runner (or in F-Droid's own build server image) and compare it with the published `lite` APK: `apksigcopier compare UltimateKeys-1.0.0-lite.apk --unsigned app-lite-release-unsigned.apk` (or `diffoscope`).
2. If the native libraries differ only because of the toolchain image, document it here and keep the fallback below.

Fallback if the build cannot be made reproducible: drop `Binaries` and `AllowedAPKSigningKeys` from the metadata, so F-Droid signs with its own key. Users would then not be able to switch between GitHub and F-Droid builds without uninstalling.

## F-Droid recipe (`fdroid/com.qtekfun.ultimatekeys.yml`)

The file has no comments because fdroiddata's tools remove them, so the choices are listed here.

| Field | Why |
|---|---|
| `Binaries` | Our signed `lite` APK of the same tag; F-Droid compares its build with it and ships ours. |
| `AllowedAPKSigningKeys` | SHA-256 of the release certificate (64 hex digits, no colons). |
| `UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` | Only final tags: release candidates (`-rc.N`) are not offered to F-Droid users. |
| `AutoUpdateMode: Version` | A new final tag adds a build entry by itself; its `versionCode` is computed as in `Versions.kt`. |
| `submodules: true` | `third_party/whisper.cpp` is a git submodule (pinned to a release tag). |
| `sudo` | Installs JDK 21, which the build needs. |
| `prebuild` | Runs `:dictionaries:fetchDictionaries` and `:emoji:generateEmojiData`, which download pinned files over the network and verify their SHA-256 into the Gradle cache. F-Droid builds without network after `prebuild`, so the later Gradle step finds them cached. |
| `gradle: lite` | Only the `lite` flavor (see the decision above). |
| `ndk: r28c` | `28.2.13676358`, the version pinned in `build-logic`. |

The build entry names `1.0.0` because that is the first release meant for F-Droid; the tag `v1.0.0` does not exist until Phase 9 task 9.6. The owner opens the fdroiddata merge request after the tag exists (`docs/HUMAN_TASKS.md`) and runs `fdroid lint`, `fdroid rewritemeta` and `fdroid build -l com.qtekfun.ultimatekeys` first.

Open points for the owner or for Phase 7:

- TODO (Phase 7): `lite` will add a WorkManager model downloader. If it adds Gradle tasks that fetch anything at build time, add them to `prebuild`; the `lite` APK itself must not contain a model.
- TODO (Phase 7): confirm that F-Droid's scanner has no complaint about the `INTERNET` permission or the model URL (an anti-feature such as `NonFreeNet` is not expected, since the download only fetches Whisper weights under the MIT license and only when the user asks).
- Check that the `prebuild` Gradle invocation finds the Android SDK and NDK in the build server image; if it does not, move the fetch into the `gradle` step by relying on the normal build (the same tasks run as part of it).
- Per-version texts come from `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` (at most 500 bytes each), title, short and full description from the same tree, screenshots from `images/phoneScreenshots/`.
