# PLAN.md — UltimateKeys implementation plan

Execute phases in order. Each phase = one branch + one PR + one release (see `CLAUDE.md`).
Tick tasks (`- [x]`) as you complete them. Tasks marked **[HUMAN]** are not done by you: add them to `docs/HUMAN_VERIFICATION.md` and continue.

Versioning (see `docs/CI_CD.md`): each phase ends with a release made by pushing a tag. Phases 0–8 release `0.1.0` … `0.9.0`; Phase 9 releases `1.0.0-rc.1` (more `rc.N` if fixes are needed) and then `1.0.0`; Phase 10 releases `1.1.0`.

---

## Phase 0 — Bootstrap, CI and releases

**Goal:** an empty but complete multi-module project with both flavors, full CI and automatic releases.

- [x] 0.1 Initialize the repo `qtekfun/UltimateKeys`; move this spec package (`CLAUDE.md`, `SPEC.md`, `PLAN.md`, `KICKOFF.md`, `docs/*`) into the root; create `PROGRESS.md`.
- [x] 0.2 Gradle (Kotlin DSL) with version catalog, latest stable AGP/Kotlin/Compose BOM, JDK 21, `minSdk 31`, compile/target latest stable. Convention plugins in `build-logic/` for Android library, Compose, native and test setup.
- [x] 0.3 Modules from `SPEC.md` §11 (except `:gesture`), each compiling with a placeholder.
- [x] 0.4 Product flavors `full` and `lite` (dimension `distribution`), same `applicationId` `com.qtekfun.ultimatekeys`.
- [x] 0.5 Gradle task `verifyFullHasNoInternet` on merged manifests of all `full` variants; hooked into `check`.
- [x] 0.6 `appVersion` in `gradle.properties` and derived `versionCode` (unit-tested), release signing from `UK_*` env vars, Gradle dependency verification, detekt — all mirroring UltimateDeck; no secrets in the repo.
- [x] 0.7 Spotless + ktlint, clang-format for own C++, Kover coverage, Dependabot, `.editorconfig`, `.gitignore` (models, keystores, `local.properties`).
- [x] 0.8 Workflows `ci.yml`, `dependabot-verification.yml` and `release.yml` as in `docs/CI_CD.md` (actions pinned by SHA); `CHANGELOG.md` (Keep a Changelog) and `RELEASING.md`.
- [x] 0.9 Repo files: `README.md`, `LICENSE` (GPL-3.0), `CONTRIBUTING.md`, `SECURITY.md`, `docs/THIRD_PARTY.md`, issue/PR templates, labels (`blocked`, `privacy`, `voice`, `ui`, `engine`, `style`).
- [x] 0.10 F-Droid: metadata skeleton `fastlane/metadata/android/en-US/` and `es-ES/`, and draft `fdroid/com.qtekfun.ultimatekeys.yml` mirroring UltimateDeck's.
- [x] 0.11 ADRs: `0001-from-scratch-with-aosp-engine.md`, `0002-module-layout.md`, `0003-dependency-injection.md`, `0004-versioning-and-releases.md`, `0005-compose-keyboard-surface.md`.
- [x] 0.12 Assess F-Droid acceptance of the `full` flavor; write `docs/DISTRIBUTION.md`.
- [ ] 0.13 Add branch protection on `master` (required check `CI / check`, linear history) via `gh api`; if not permitted, add it to `docs/HUMAN_VERIFICATION.md`.

**DoD:** CI green on both flavors; tag `v0.1.0` pushed; Release workflow published both APKs (or failed only for missing signing secrets, noted in `PROGRESS.md`).

---

## Phase 1 — IME core

**Goal:** a usable keyboard that types correctly in any app, without suggestions yet.

- [x] 1.1 `InputMethodService` + IME declaration (`method.xml`, subtypes `es`, `en`); onboarding screens in `:app` to enable and select the keyboard.
- [x] 1.2 Input connection layer: commit/composing text, batch edits, selection tracking, restarts, password fields; heavily unit-tested with a fake `InputConnection`.
- [x] 1.3 `:layouts`: data format + parser; layouts ES QWERTY (with `ñ`), EN QWERTY, symbols (2 pages), numeric, phone, number-row variant; long-press alternatives per layout.
- [x] 1.4 Keyboard surface in Compose: Canvas-based key grid, hit testing with touch-area correction, multi-touch, key popups, long-press chooser with slide-to-select. Uses a temporary default style (real style engine in Phase 3).
- [x] 1.5 Shift/caps lock, auto-capitalization, double-space period, smart punctuation spacing, enter key editor actions.
- [x] 1.6 Delete repeat with acceleration, swipe-left delete word; spacebar cursor control; delete-drag selection.
- [x] 1.7 Haptics and sound with settings; keyboard height and number row settings; settings in DataStore.
- [x] 1.8 Macrobenchmark/tracing harness for key-press latency; record first numbers in `docs/PERFORMANCE.md`.
- [x] 1.9 Emulator smoke test: enable IME, type into an `EditText`, verify text.

**DoD:** types correctly in ES/EN layouts in the emulator test; latency harness in place; release `v0.2.0`.

---

## Phase 2 — Suggestion engine

- [x] 2.1 Vendor AOSP LatinIME `native/jni` at a pinned commit into `third_party/aosp-latinime/` with `LICENSE`, `NOTICE`, `UPSTREAM_COMMIT`.
- [x] 2.2 CMake build for all ABIs; adapt JNI registration to our Kotlin classes; record changes in `MODIFICATIONS.md`.
- [x] 2.3 `:engine` Kotlin API (`SuggestionEngine`) with JNI bindings: open dictionary, suggest, predict next, validity check, user history learning, user dictionary.
- [x] 2.4 `:dictionaries`: select `es` and `en` dictionaries with compatible licenses (document source and license in `docs/THIRD_PARTY.md`); fetch at build time with checksum or vendor; load from assets.
- [x] 2.5 Mixed ES/EN: query both, merge with language-confidence weighting from recent context; unit tests with fixed engine fakes and a small real-dictionary integration test.
- [x] 2.6 Suggestion strip (basic look), tap to pick, autocorrect on space/punctuation, backspace-undo of autocorrect, next-word prediction.
- [x] 2.7 User dictionary screen (add, remove, import/export), "clear learned data".
- [x] 2.8 Emulator JNI test for the engine; latency check of suggestion updates against `SPEC.md` §12.
- [x] 2.8b Reproducible-build CI job (build release twice in clean dirs, compare APKs); fix native non-determinism or document the fallback in `docs/DISTRIBUTION.md`.
- [ ] 2.9 **[HUMAN]** Real typing feel and autocorrect quality check on the Find X8 Pro.

**DoD:** suggestions and autocorrect working in ES, EN and mixed; release `v0.3.0`.

---

## Phase 3 — Style engine

- [x] 3.1 `:style` schema (versioned, Kotlin serialization) with every parameter in `SPEC.md` §6; validation, clamping and schema migration.
- [x] 3.2 Theming runtime: the keyboard surface, suggestion bar and panels read everything from the active Style; live updates without IME restart.
- [x] 3.3 Material You dynamic colors and light/dark variants per style.
- [x] 3.4 Bundle open fonts (at least 4, e.g. Inter, Roboto Flex, Atkinson Hyperlegible, a rounded one) and Material Symbols; licenses recorded.
- [x] 3.5 At least 7 original presets with clearly different looks; names and descriptions are ours.
- [x] 3.6 Style editor in `:app`: grouped controls, live keyboard preview, reset, duplicate, contrast warning (WCAG AA).
- [x] 3.7 Import/export `.ukstyle` via SAF and share sheet; round-trip tests.
- [x] 3.8 Bottom-row arrangement and suggestion-bar layout options wired into `:layouts` / `:ime`.
- [x] 3.9 Screenshot tests for every preset in light and dark.
- [x] 3.10 Originality check script in CI: fails if strings, preset names or identifiers contain other keyboard product names (list in the script). Scans module sources, resources, presets and `fastlane/`; excludes the spec documents (`SPEC.md`, `CLAUDE.md`, `PLAN.md`), which name those products only to forbid them.

**DoD:** presets selectable and editable live; screenshot tests green; release `v0.4.0`.

---

## Phase 4 — Private mode

- [x] 4.1 `:privacy`: `PrivacyState` as `StateFlow` and a pure `shouldBePrivate(editorInfo, manualToggle, settings)`.
- [x] 4.2 Auto activation for `IME_FLAG_NO_PERSONALIZED_LEARNING`, password, visible-password, web-password and number-password types.
- [x] 4.3 Manual toggle with "lasts until turned off / keyboard closes" setting.
- [x] 4.4 Suppress all learning (engine history, user dictionary auto-add, dictation results) while private.
- [x] 4.5 Indicator icon + style-defined tint; accessible content description.
- [x] 4.6 Tests: 100% coverage of rules; integration tests proving no learning writes while private.

**DoD:** release `v0.5.0`.

---

## Phase 5 — Clipboard and emoji

- [x] 5.1 Clipboard history in Room: retention options, max items, pin, delete, clear all; never stores private-mode or sensitive-flagged clips.
- [x] 5.2 Clipboard panel UI (styled).
- [x] 5.3 Emoji panel: categories, recents, skin tones (styled).
- [x] 5.4 CLDR annotations `en`/`es` processed at build time into a compact index; accent-insensitive prefix search with ranking.
- [x] 5.5 Tests: retention, sensitive filtering, search ranking in both languages.

**DoD:** release `v0.6.0`.

---

## Phase 6 — Voice core (whisper.cpp)

- [x] 6.1 whisper.cpp git submodule at a release tag; CMake for all ABIs; release flags `-O3`, NEON on ARM.
- [x] 6.2 JNI + `SpeechTranscriber` API; model load/unload; threads on big cores.
- [x] 6.3 Audio capture 16 kHz mono float in memory; VAD auto-stop with configurable silence timeout.
- [x] 6.4 Language auto-detect restricted to `es`/`en`; force options.
- [x] 6.5 Voice panel UI (styled): listening animation, level meter, stop, cancel, transcribing, error states.
- [x] 6.6 `RECORD_AUDIO` flow via transparent activity.
- [x] 6.7 Insertion with spacing/capitalization; single undo step; respects private mode.
- [x] 6.8 Tests with fake transcriber; emulator JNI smoke test with `tiny` model (CI cache only).
- [ ] 6.9 **[HUMAN]** Dictation quality in ES, EN and mixed on the Find X8 Pro.

**DoD:** dictation end to end on emulator with a provided model; release `v0.7.0`.

---

## Phase 7 — Models and flavors

- [ ] 7.1 `docs/MODELS.md` + `models.json`: `base` and `small` (quantized), size, SHA-256 (compute and pin from the official source), URL.
- [ ] 7.2 `full`: build-time download of `base` with checksum, cached; packaged uncompressed as asset.
- [ ] 7.3 `lite`: WorkManager downloader (resumable, Wi-Fi-only option) + SHA-256; INTERNET only in `lite` manifest.
- [ ] 7.4 Import from file (SAF) in both flavors with checksum check / unknown-file warning.
- [ ] 7.5 Model manager and dictation settings screens.
- [ ] 7.6 Tests: checksum, manifests of both flavors, downloader state machine.

**DoD:** `full` dictates offline out of the box; `lite` downloads and verifies; release `v0.8.0`.

---

## Phase 8 — Polish, performance, accessibility, translation

- [ ] 8.1 Meet the latency targets in `SPEC.md` §12; optimize and document in `docs/PERFORMANCE.md` (including APK sizes per flavor).
- [ ] 8.2 Model warm-up on voice panel open, unload after idle; memory caps.
- [x] 8.3 Accessibility: TalkBack for keys (explore-by-touch) and all panels/settings; 48 dp targets in settings.
- [x] 8.4 Complete Spanish translation.
- [x] 8.5 In-app "Open-source licenses" screen generated from `docs/THIRD_PARTY.md` data.
- [x] 8.6 Zero lint warnings in our modules; coverage targets met.
- [ ] 8.7 **[HUMAN]** Real-device benchmarks, battery, 30-minute stability session (Find X8 Pro and a Pixel).

**DoD:** release `v0.9.0`.

---

## Phase 9 — Release material and 1.0

- [x] 9.1 Original app icon (adaptive + monochrome), Ultimate family style.
- [x] 9.2 Screenshots generated from screenshot tests into `fastlane/metadata/android/*/images/phoneScreenshots/` (show several presets).
- [x] 9.3 Store texts EN + ES and 1.0.0 changelog; no references to other products.
- [x] 9.4 Final README: features, style engine, flavors, privacy statement, install (GitHub, Obtainium, F-Droid status), build, credits.
- [x] 9.5 Check every acceptance criterion in `SPEC.md` §14 that can be automated; list the rest in `docs/HUMAN_VERIFICATION.md`.
- [ ] 9.6 Release `1.0.0-rc.1` (pre-release); fix anything found, more `rc.N` if needed; then release `1.0.0` with both APKs and checksums.
- [ ] 9.7 Finalize `fdroid/com.qtekfun.ultimatekeys.yml` (`Binaries`, `AllowedAPKSigningKeys` placeholder, `UpdateCheckMode: Tags`) and verify reproducibility of the tagged build; **[HUMAN]** submit to fdroiddata.
<!-- 9.7 prepared (recipe, reproducibility notes in docs/DISTRIBUTION.md); open: verify the tagged 1.0.0 build and submit to fdroiddata -->

**DoD:** `v1.0.0` published.

---

## Phase 10 — Gesture typing (1.1)

- [x] 10.1 Research note `docs/gesture/DESIGN.md`: algorithm (SHARK2-style template matching: shape + location channels), cited papers, complexity budget.
- [x] 10.2 `:gesture` module: path capture and resampling, gesture trail rendering (styled), templates generated from the current layout geometry.
- [x] 10.3 Candidate generation (pruning by start/end keys and path length) and scoring combined with the engine's language model and mixed ES/EN weighting.
- [x] 10.4 Commit behavior: auto-space between gestured words, alternatives in suggestion strip, backspace removes whole gestured word.
- [x] 10.5 Settings: enable, trail on/off, sensitivity.
- [x] 10.6 Offline evaluation harness with synthetic gestures for a word list; track top-1 / top-3 accuracy in `docs/gesture/RESULTS.md`; target top-3 ≥ 90% on the 5,000 most common words per language.
- [x] 10.7 Latency: candidate list < 50 ms p95 after finger lift.
- [ ] 10.8 **[HUMAN]** Real gesture typing feel on the Find X8 Pro.

**DoD:** release `v1.1.0`; final summary delivered to the owner.
