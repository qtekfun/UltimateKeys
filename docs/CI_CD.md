# CI/CD, versioning and releases

Same model as the sibling project **UltimateDeck** (`https://github.com/qtekfun/UltimateDeck`, public). When in doubt, read its `.github/workflows/`, `RELEASING.md`, `CHANGELOG.md`, `app/build.gradle.kts`, `gradle.properties` and `fdroid/` and mirror them, adapting names (`UD_` → `UK_`) and adding what this project needs on top (two flavors, native code, emulator tests).

No release-please. Releases are created by pushing a version tag.

## Branching and merge policy

- Default branch: `master` (as in UltimateDeck). It is always releasable. Work happens on `phase/<n>-<slug>` branches; squash-merge only.
- Branch protection on `master`: required check `CI / check` (plus `CI / emulator-tests` once it exists), linear history.
- Conventional Commits for commit messages and PR titles.

## Versioning

- The version lives in one place: `appVersion` in `gradle.properties`, SemVer `X.Y.Z` or `X.Y.Z-rc.N` for release candidates.
- `versionCode` is derived, never set by hand: `(MAJOR*10000 + MINOR*100 + PATCH) * 100 + N`, with `N = 99` for a final release (so `1.0.0-rc.1` = `1000001`, `1.0.0` = `1000099`). Implement `versionCodeOf()` exactly like UltimateDeck and unit-test it.
- `versionName = appVersion`. Both flavors share `applicationId` and `versionCode`.
- Before 1.0.0 the app is `0.x`.

## Changelog

- `CHANGELOG.md` in [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format, with an `## [Unreleased]` section.
- Every PR that changes user-visible behavior adds its lines under `[Unreleased]` (Added / Changed / Fixed / Removed), written for users, not developers.
- Compare links at the bottom, as in UltimateDeck.

## Workflow `ci.yml`

Triggers: `pull_request` and `push` to `master`. `permissions: contents: read`. Concurrency group per ref, cancel in progress. Skips Dependabot PRs (handled by `dependabot-verification.yml`).

Job `check` (ubuntu-latest, timeout ~45 min):
1. `actions/checkout` with `persist-credentials: false` and `submodules: recursive`.
2. `actions/setup-java` Temurin 21; `gradle/actions/setup-gradle` (also validates the wrapper checksum).
3. Pinned NDK and CMake for `:engine` and `:voice`; `ccache` for native builds.
4. Cache downloaded dictionaries and Whisper models (keys = their pinned SHA-256).
5. `./gradlew check assembleDebug` — `check` must include: unit tests of all modules and both flavors, lint, detekt/ktlint, `verifyFullHasNoInternet`, `originalityCheck`, coverage verification, screenshot tests.
6. On failure, upload reports (retention 14 days).

Job `emulator-tests` (from Phase 1; KVM): `reactivecircus/android-emulator-runner`, API 34 x86_64, `connectedFullDebugAndroidTest` (IME smoke flows, engine JNI, and from Phase 6 whisper JNI with the cached `tiny` model).

All third-party actions **pinned by commit SHA** with the version in a comment, as in UltimateDeck. SPDX headers on workflow files and sources (`GPL-3.0-or-later`, "UltimateKeys contributors").

## Workflow `dependabot-verification.yml`

Same as UltimateDeck: Gradle dependency verification is on (`gradle/verification-metadata.xml`, sha256). For Dependabot PRs, the workflow refreshes the checksums on the PR branch, commits them, then runs the same checks in strict mode.

## Workflow `release.yml`

Trigger: `push` of tags `v*`. `permissions: contents: write`.

1. Checkout (`persist-credentials: false`, submodules), same toolchain setup as CI.
2. Fail if the tag does not equal `v` + `appVersion` from `gradle.properties`.
3. Decode `UK_KEYSTORE_BASE64` into `$RUNNER_TEMP/release.jks`, export `UK_KEYSTORE_FILE`; secrets `UK_KEYSTORE_PASSWORD`, `UK_KEY_ALIAS`, `UK_KEY_PASSWORD` as env. If any secret is missing, fail with `Signing secrets missing — see docs/HUMAN_TASKS.md`.
4. `./gradlew check assembleFullRelease assembleLiteRelease`; delete the keystore afterwards (`if: always()`).
5. Copy APKs to `UltimateKeys-<version>-full.apk` and `UltimateKeys-<version>-lite.apk`; generate `SHA256SUMS.txt`.
6. Extract the notes for that version from `CHANGELOG.md` (same `awk` as UltimateDeck).
7. `gh release create <tag> <apks> SHA256SUMS.txt --title "UltimateKeys <version>" --notes-file notes.md`, adding `--prerelease` for `-rc.N` tags. Uses `github.token`; no personal token needed.

## Making a release (done by Claude Code at the end of every phase)

1. Move the `[Unreleased]` notes in `CHANGELOG.md` under `## [X.Y.Z] - YYYY-MM-DD`; update compare links.
2. Set `appVersion=X.Y.Z` in `gradle.properties`.
3. PR `chore: release X.Y.Z` → CI green → squash-merge to `master`.
4. `git checkout master && git pull && git tag vX.Y.Z && git push origin vX.Y.Z`.
5. Watch the Release workflow (`gh run watch`). If it fails only because signing secrets are missing, note it in `PROGRESS.md` and continue; once the owner adds the secrets, re-run it (`gh run rerun`).
6. Document the procedure in `RELEASING.md` (mirror UltimateDeck's).

## Release signing in Gradle

```kotlin
val releaseKeystore: String? = System.getenv("UK_KEYSTORE_FILE")

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
```

Without these variables `assembleRelease` builds unsigned APKs — what F-Droid does before comparing.

## Dependabot

`.github/dependabot.yml` as in UltimateDeck (`gradle` and `github-actions` weekly, grouped, prefixes `build`/`ci`), plus `gitsubmodule` for whisper.cpp (monthly). Claude Code may merge Dependabot PRs during a phase if verification is green and the bump is patch/minor.

The vendored AOSP engine is pinned (`third_party/aosp-latinime/UPSTREAM_COMMIT`) and only updated deliberately.

## F-Droid and reproducible builds

- Goal, as in UltimateDeck: **reproducible builds**. F-Droid builds the same tag, checks its APK matches ours (`Binaries`, `AllowedAPKSigningKeys`) and ships ours, so users can update from GitHub or F-Droid interchangeably.
- Requirements: no timestamps or machine paths in the build, pinned JDK/AGP/NDK/CMake, deterministic native flags (`-ffile-prefix-map`, no build IDs varying between machines), stable resource ordering. Add a CI job (from Phase 2) that builds the release twice in clean directories and compares the APKs (`diffoscope` or `apksigcopier compare`) to catch regressions.
- If native reproducibility cannot be achieved, document why and the fallback (F-Droid signs with its own key) in `docs/DISTRIBUTION.md`.
- Metadata draft in `fdroid/com.qtekfun.ultimatekeys.yml` (no comments, `UpdateCheckMode: Tags`), and `fastlane/metadata/android/` kept current; per-version changelogs in `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` generated from `CHANGELOG.md` during the release PR.
- The `full` flavor (bundled model downloaded at build time) may not be acceptable to F-Droid; decision in `docs/DISTRIBUTION.md`.
