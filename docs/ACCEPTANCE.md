<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Acceptance audit (1.0)

Every acceptance criterion of `SPEC.md` section 14 and every quality target of section 12, with where it is checked.
"Automated" means a test or build task that runs in CI (`CI / check` unless another job is named) and fails the build.
"Human" means it needs a device or a judgment and has an item in [HUMAN_VERIFICATION.md](HUMAN_VERIFICATION.md).
Audit made on 2026-10-08 against `master` at the end of Phase 10; rows marked "Phase 7" or "Phase 8" are still open
because those phases are not merged yet.

## Section 14: acceptance criteria

| # | Criterion | Status | Where it is checked |
|---|---|---|---|
| 1 | Typing in Spanish, English and mixed text with autocorrect and predictions works reliably | Logic automated, feel human | Unit: `InputLogicTest`, `SuggestionTest`, `MixedSuggestionEngineTest` (mixed ES/EN merge), `AospSuggestionEngineTest`, `KeyboardControllerTest`, `GestureCommitTest`. Emulator job `CI / emulator-tests`: `TypingSmokeTest`, `EngineJniTest` (real engine, both dictionaries). Human: Phase 1 and 2 items (typing feel, autocorrect quality). |
| 1b | Performance targets of section 12 met "on CI benchmarks where measurable" | Partly automated | Suggestions < 30 ms p95: `EngineJniTest.suggestionLatencyStaysUnderTarget` (emulator, engine calls only). Gesture candidates < 50 ms: `GestureLatencyTest`. Key-to-commit and first show: human (see section 12 below). |
| 2 | At least 7 built-in presets with clearly different looks | Automated | `PresetsTest` (`there are at least seven presets with unique ids and names`, `presets look clearly different from each other`, readable labels in every variant). Looks: `PresetScreenshotTest` goldens. |
| 3 | A style exported and re-imported is identical | Automated | `StyleCodecTest` (`a style survives a round trip unchanged`), `PresetsTest` (`every preset ... survives a round trip`), `StyleRepositoryTest`. |
| 4 | No preset, string or identifier references another keyboard product | Automated | `originalityCheck` (part of `check`, scans code, resources, docs and the fastlane store texts) and its own test `OriginalityScannerTest`. Reviewed by hand once in this audit for the README and store texts. |
| 5 | `full` APK has no INTERNET permission | Automated | `verifyFullHasNoInternet` (merged manifests of every `full` variant; part of `check`). The `lite` manifest must declare INTERNET (`verifyLiteHasInternet`, `ManifestRulesTest`); the downloader is `app/src/lite` only (task 7.6, done). |
| 5b | ... and dictation works in airplane mode | Human, by construction | Dictation runs through `WhisperTranscriber` with no network code on the path, and the `full` build cannot open a socket without the permission. Emulator `WhisperJniTest` transcribes a sample with a local model. The bundled model of `full` is built by `fetchBundledModel` (7.2, done). Human: transcribe with airplane mode on (item added). |
| 6 | Dictating a ~10-word sentence with `base` returns text in at most 2 s on the Find X8 Pro | Human | Needs the device and the `base` model. `UKVoice` log tag reports real-time factor in debug builds. Item added. |
| 7 | In an incognito browser tab, private mode activates automatically and no new words are learned | Logic automated, real browser human | `PrivateModeTest` (`an incognito field never reaches the engine's learning`, password fields, manual switch), `PrivacyRulesTest`, `PrivacyStateTest`. Human: a real incognito tab in a browser (item added). |
| 8 | Copied text in private mode never appears in clipboard history | Automated | `ClipboardHistoryTest` (`nothing is stored while private mode is on`, `sensitive clips are never stored`). Human check in a real app is listed under Phase 5. |
| 9 | No crashes in a 30-minute mixed typing + dictation session | Human | Phase 8 task 8.7 (item added). |
| 10 | All third-party components credited in-app and in `docs/THIRD_PARTY.md` | Partly automated | `docs/THIRD_PARTY.md` lists every component and the README repeats the credits. The in-app licenses screen is Phase 8 (task 8.5). |

## Section 12: quality

| Item | Status | Where it is checked |
|---|---|---|
| Unit tests: private-mode rules | Automated | `PrivacyRulesTest`, `PrivacyStateTest`, `PrivateModeTest`. |
| Unit tests: input-connection logic, layout parsing, style serialization, suggestion merging, clipboard retention, emoji search | Automated | `InputLogicTest`, `LayoutParserTest`, `StyleCodecTest`, `MixedSuggestionEngineTest`, `ClipboardHistoryTest` (retention), `EmojiSearchTest`. |
| Unit tests: model verification and downloader state machine | Automated | Task 7.6 (done): `ModelCatalogTest`, `StoreAndImportTest`, `ModelDownloaderTest`, `ProvisioningAndControllerTest`, `HttpUrlConnectionSourceTest`, `ManifestRulesTest`. |
| Coverage 80% on Kotlin modules | Automated | `koverVerify` in `check` (minimum 80% lines, vendored code and glue listed in the root `build.gradle.kts` excluded). |
| Coverage 100% on private-mode rules | Automated (added in this audit) | `:privacy:koverVerify` (rule `Private mode rules: full line coverage`, part of `check`). It was only 80% overall before. |
| Screenshot tests for every built-in preset, light and dark | Automated | `PresetScreenshotTest` (every preset in light and dark), plus private mode, panels and voice states, all compared with the goldens in `screenshots/src/test/snapshots`. |
| Emulator tests for IME smoke flows and JNI (engine and whisper with `tiny`) | Automated | `CI / emulator-tests`: `TypingSmokeTest`, `EngineJniTest`, `WhisperJniTest`. |
| Key press to character committed: < 16 ms p95 on the Find X8 Pro | Human | Measured by `LatencyTracker` (`UKLatency` log tag); to be recorded in `docs/PERFORMANCE.md` (Phase 8, task 8.1 and 8.7). Only a Pixel 8 figure of our own processing exists today. |
| Suggestions updated: < 30 ms p95 after each keystroke | Engine automated, device human | `EngineJniTest.suggestionLatencyStaysUnderTarget` on the CI emulator; the device figure is human. |
| Keyboard first show after IME start: < 300 ms | Human | Not measured yet; add a first-show trace in Phase 8 (item added). |
| CI on GitHub Actions, Conventional Commits, Keep a Changelog | Automated | `ci.yml`, `release.yml`; the release workflow takes its notes from `CHANGELOG.md`. |
| Reproducible builds | Automated | `CI / reproducible-build` builds both flavors twice in different directories and compares the unsigned APKs. Comparison with F-Droid's build: human, see `docs/DISTRIBUTION.md`. |

## Gaps found and what was done

- Fixed: the 100% coverage requirement for private-mode rules was not enforced; `:privacy:koverVerify` now enforces it and `check` depends on it.
- Not fixable now (open phases): lite manifest and downloader tests (Phase 7), the licenses screen (Phase 8.5), latency figures and a first-show measurement (Phase 8.1 and 8.7).
- Before tagging `1.0.0`: re-read `fastlane/metadata/android/*/full_description.txt` and the README against what is on `master`. The store texts describe the finished 1.0 (both flavors, speech models you can pick); Phase 7 (flavors, models, model manager) is merged.
