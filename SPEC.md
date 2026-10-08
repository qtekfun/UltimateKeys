# UltimateKeys — Product & Technical Specification

> Spec-driven development document for Claude Code.
> Package: `com.qtekfun.ultimatekeys` · License: GPL-3.0 (same as the rest of the Ultimate family; third-party components keep their own licenses)
> All code, comments, commits, issues, docs and UI strings (default locale) are in **English**. Spanish is an additional UI translation.

---

## 1. Vision

A modern Android keyboard, **written from scratch**, with:

- **A fully configurable look**: a style engine where every visual aspect (key shape, spacing, borders, shadows, fonts, colors, popups, suggestion bar layout, bottom row) is a parameter. Users can make it look minimal, rounded, flat, classic, or anything in between, and share styles as files.
- **100% offline**: no network needed for typing, suggestions or voice dictation.
- **Private by design**: a real private mode; no telemetry, analytics or cloud.
- **Offline voice dictation** powered by whisper.cpp.
- High-quality typing from day one by reusing the proven open-source AOSP suggestion engine (Apache-2.0) as a dependency, with credit.

It is the fourth member of the "Ultimate" app family (UltimateDeck, UltimateTasks, UltimateCalendar) and follows the same conventions as UltimateDeck: tag-based releases, hand-written changelog, reproducible builds, F-Droid-ready from day one.

### Originality rules (mandatory)
- The app's code is original. Third-party code is used only as clearly separated, credited dependencies under `third_party/` with their licenses and a record of modifications.
- The visual design is original. Built-in style presets have our own names and our own look. Never name, describe or market a preset or feature as a copy of another product (no "Gboard", "iOS", "Apple", "Samsung", "SwiftKey" in presets, store texts or code identifiers).
- Generic keyboard conventions are fine (QWERTY, key popups, long-press accents, spacebar cursor control, suggestion strip).
- No proprietary assets: fonts and icons must be open-licensed (e.g. Inter, Roboto Flex, Atkinson Hyperlegible, Material Symbols). Record each in `docs/THIRD_PARTY.md`.

### Non-goals (MVP)
- GIFs, stickers or any online content.
- Cloud sync, accounts, translation.
- One-handed and floating modes (post-1.0 backlog).

---

## 2. Third-party components

| Component | Use | License | How |
|---|---|---|---|
| AOSP LatinIME native engine (`packages/inputmethods/LatinIME/native/jni`) | Dictionary lookup, autocorrect, next-word prediction, user history learning | Apache-2.0 | Vendored copy under `third_party/aosp-latinime/` at a pinned commit, built with CMake |
| AOSP-format dictionaries for `es` and `en` | Word lists for the engine | Verify per file | Fetched at build time from a pinned source with checksum, or vendored if small; only files with licenses compatible with GPL-3.0 and F-Droid |
| whisper.cpp | Offline speech-to-text | MIT | Git submodule pinned to a release tag |
| Unicode CLDR annotations | Emoji search keywords (`en`, `es`) | Unicode License | Processed at build time into an asset |
| Open fonts / Material Symbols | Style engine | OFL / Apache-2.0 | Bundled with license files |

Rules:
- Only the native engine is taken from AOSP. All Java/Kotlin IME code (service, keyboard view, layouts, settings) is written from scratch.
- The JNI glue in the AOSP code registers natives against Java class names; adapt it to our own Kotlin classes. List every modified file in `third_party/aosp-latinime/MODIFICATIONS.md` (Apache-2.0 requires stating changes) and keep its `NOTICE`/`LICENSE`.
- All credits appear in `README.md`, `docs/THIRD_PARTY.md` and an in-app "Open-source licenses" screen.

---

## 3. Platform

- **minSdk 31 (Android 12)**, target/compile SDK: latest stable.
- Kotlin only for app code; C++ for vendored native code. Coroutines + Flow.
- UI: **Jetpack Compose** for the keyboard surface, panels and settings. The key area must be drawn efficiently (single Canvas-based layout for the key grid, no per-key composable recomposition storms). If Compose cannot meet the latency targets in §12, switch the key grid alone to a custom `View` and record it in an ADR.
- NDK + CMake for the AOSP engine and whisper.cpp. ABIs: arm64-v8a (mandatory), armeabi-v7a (optional), x86_64 (emulator/CI).
- Primary test device: OPPO Find X8 Pro (ColorOS). Must also work on a Pixel running stock Android.

---

## 4. IME core

- `InputMethodService` implementation with robust `InputConnection` handling: composing text, batch edits, cursor tracking (`onUpdateSelection`), restarts, password fields, apps with broken editors.
- Layouts defined as data (JSON or Kotlin DSL) in `:layouts`: Spanish QWERTY (with `ñ`), English QWERTY, one or more layouts for each other supported language (ADR 0022), symbols (2 pages), numeric, phone, number row variant. The supported languages are listed as data in `:languages`; right-to-left languages are out of scope.
- Shift: single tap, double tap caps lock, auto-capitalization following `EditorInfo` caps mode.
- Long-press: accented and alternative characters per layout (e.g. `a → á à ä â ã å ā`), popup chooser with slide-to-select.
- Delete key with accelerating repeat and swipe-left-to-delete-word.
- **Spacebar cursor control**: long-press or horizontal drag on space moves the cursor; drag on delete selects text to delete.
- Double-space inserts period; smart punctuation spacing.
- Editor actions (Go, Search, Send, Next, Done) on the enter key with matching icon.
- Haptics and sounds with configurable intensity/volume.
- Settings: keyboard height, number row, long-press delay, all of the above toggles.

---

## 5. Suggestions and autocorrect

- `:engine` module wraps the native engine behind a Kotlin `SuggestionEngine` interface: `suggest(context, composing, locale)`, `predictNext(context)`, `learn(word, context)`, `isValidWord()`, `addToUserDictionary()`, `clearLearned()`.
- **Mixed typing in two or more languages without manual switching**: query the dictionary of every enabled language (at most six, Spanish and English by default) and merge candidates with a language-confidence weight derived from recent context (last N words). The language of the active layout is the primary one (ADR 0022).
- Suggestion strip shows up to 3 (or scrolling N, depending on style) candidates; autocorrect on space/punctuation with undo by backspace immediately after correction.
- Next-word prediction after space.
- User dictionary (add/remove words, import/export) and learned history, both disabled while private mode is on.
- Globe key/language switcher remains available for users who add more languages.

### Gesture (glide) typing
- Implemented by us in a dedicated post-1.0 phase (see PLAN), as `:gesture` module: path sampling, template matching against the keyboard geometry (SHARK2-style), candidate scoring combined with the engine's language model. Original implementation; cite the papers used in `docs/THIRD_PARTY.md`.

---

## 6. Style engine (configurable aesthetics)

The look is 100% data-driven. A **Style** is a serializable object (JSON, versioned schema) with parameters such as:

| Group | Parameters |
|---|---|
| Keys | corner radius, horizontal/vertical gap, key height per row, border on/off + width + color, shadow (none / bottom edge / elevation) + color, background color per key class (letter, function, space, enter/action) |
| Labels | font family (bundled open fonts), weight, size, letter case (always upper / follow shift), secondary hint labels on/off |
| Background | solid, gradient (2–3 stops, angle), user image (with blur and dim), Material You dynamic colors |
| Key feedback | popup style (bubble above key / enlarged key / none), press highlight color, press animation (none / scale / fade) |
| Suggestion bar | layout (3 centered with dividers / scrolling list), tool icons (shown / collapsed behind a button / hidden), height, text style |
| Bottom row | arrangement presets (e.g. `[123][emoji][space][.][enter]`, `[123][,][space][mic][enter]`, `[123][globe][space][enter]`), mic placement (bottom row / suggestion bar) |
| Panels | colors and corner radius for emoji, clipboard and voice panels |
| Motion | panel transition style and duration |

Requirements:
- **Light and dark variants** in every style, following the system or forced.
- **Built-in presets**, original and named by us (e.g. "Ultimate", "Soft", "Flat", "Outline", "Classic", "Midnight", "Paper"). They must cover clearly different looks: borderless minimal, rounded with bottom shadow, outlined keys, flat high-contrast, etc.
- **Style editor**: live preview of the keyboard while editing every parameter; reset to preset; duplicate preset.
- **Import / export** styles as `.ukstyle` (JSON) files via the Storage Access Framework and share sheet. Validate schema and clamp values on import.
- Style changes apply instantly without restarting the IME.
- Accessibility guard: warn when label/background contrast is below WCAG AA.

---

## 7. Offline voice dictation (Whisper)

### Engine
- whisper.cpp, quantized GGML models, in `:voice` behind a `SpeechTranscriber` interface.
- Inference on a dedicated background thread; never block the IME UI thread. Threads tuned to big cores.
- Debug builds only: log real-time factor and latency locally.

### Interaction
- Microphone in the suggestion bar or bottom row (depending on style).
- Tap → voice panel with listening animation and level meter → speak → auto-stop on silence (VAD) or tap again → transcribing state → text inserted at cursor.
- Cancel discards audio. Insert with correct spacing/capitalization; single undo step.
- Clear error states: no permission, no model (shortcut to model manager).

### Language
- Default: auto-detect per utterance restricted to `es`/`en`; option to force either.
- Document that in-sentence language switching is unreliable with small models; `small` handles it better.

### Models
- Model manager: supported models with size, quality notes, installed state, select, delete.
- Supported: `base` quantized (~60 MB) and `small` quantized (~190 MB).
- Sources: download from a pinned URL **verified by SHA-256**, or **import from local file** (SAF).
- Stored in app-private storage.

### Privacy
- Audio only in memory; no files on disk.
- Dictation results do not feed learning while private mode is on.
- `RECORD_AUDIO` requested on first use with explanation (via a transparent activity).

---

## 8. Build flavors

| Flavor | Model | INTERNET permission | Notes |
|---|---|---|---|
| `full` | `base` bundled in the APK | **No** — not declared at all | Fully air-gapped. Extra models only by file import. |
| `lite` | None bundled | Yes, used **only** by the model downloader | Small APK; downloads `base` or `small`, or imports a file. |

- A build check fails if INTERNET (or `ACCESS_NETWORK_STATE`) appears in any `full` merged manifest.
- Models are never committed; the `full` build downloads `base` from a pinned URL with checksum, cached by Gradle/CI.
- F-Droid: assess whether the bundled-model flavor is acceptable. If not, only `lite` goes to F-Droid and `full` to GitHub Releases. Decision in `docs/DISTRIBUTION.md`.

---

## 9. Private mode

When active:
1. **No learning**: nothing added to user dictionary, history or prediction data (typing and dictation).
2. **No clipboard history**: copied content is not stored.
3. **Visible indicator**: icon in the suggestion bar and a subtle tint defined by the active style.

Activation:
- **Automatic** on `IME_FLAG_NO_PERSONALIZED_LEARNING` (incognito tabs, private apps) and on password / sensitive input types.
- **Manual** toggle; lasts until turned off or until the keyboard closes (configurable).

Always (not only in private mode):
- No analytics, crash reporting SDKs, ads or telemetry.
- No network access outside the `lite` model downloader.
- "Clear all learned data" in settings.
- Clipboard items flagged sensitive by Android 13+ (`ClipDescription.EXTRA_IS_SENSITIVE`) are never stored.

---

## 10. Features (1.0)

- IME core (§4), suggestions and autocorrect with mixed ES/EN (§5).
- Style engine, presets, editor, import/export (§6).
- **Clipboard panel**: history with retention (1 h / 1 day / 7 days / forever), max items, pin, delete, clear all; respects private mode.
- **Emoji panel**: categories, recents, skin tones, **offline search** in English and Spanish (CLDR, accent-insensitive).
- Offline dictation and model manager (§7).
- Private mode (§9).
- Settings in Compose + Material 3; English and Spanish UI; open-source licenses screen.

### After 1.0
- **1.1: gesture typing** (§5), planned and executed autonomously.
- Backlog: one-handed and floating modes, text-editing panel, more layouts and languages, style gallery.

---

## 11. Architecture

Gradle modules:

- `:app` — settings app, onboarding (enable IME, select IME), style editor, model manager.
- `:ime` — `InputMethodService`, input connection logic, keyboard surface, panels host.
- `:layouts` — layout definitions and parser.
- `:style` — Style schema, presets, serialization, validation, theming runtime.
- `:engine` — Kotlin API + JNI + vendored AOSP native engine.
- `:dictionaries` — dictionary fetching/packaging and loading.
- `:privacy` — private mode state and rules.
- `:clipboard`, `:emoji` — panels and their data.
- `:voice` — whisper.cpp JNI, audio capture, VAD.
- `:voice-models` — model catalog, download (lite only), import, verification.
- `:gesture` — added in the 1.1 phase.
- `:core` — shared utilities, settings storage (DataStore), DI.

Guidelines:
- Interfaces between modules (`SuggestionEngine`, `SpeechTranscriber`, `StyleRepository`, `PrivacyPolicy`) so implementations can be swapped and faked in tests.
- DI: manual DI or Hilt — decide in Phase 0 and justify in an ADR.
- Settings in DataStore; clipboard history in Room.
- ADRs in `docs/adr/`.

---

## 12. Quality

- Unit tests for: private-mode rules (100%), input-connection logic, layout parsing, style serialization/validation/migration, suggestion merging for mixed languages, clipboard retention, emoji search, model verification, downloader state machine.
- Coverage ≥ 80% on Kotlin modules (excluding vendored code).
- Screenshot tests (Paparazzi or Roborazzi) for every built-in preset, light and dark.
- Instrumented/emulator tests for IME smoke flows and JNI (engine and whisper with `tiny` model).
- Performance targets (measured with Macrobenchmark / custom tracing, recorded in `docs/PERFORMANCE.md`):
  - key press → character committed: < 16 ms p95 on the Find X8 Pro;
  - suggestions updated: < 30 ms p95 after each keystroke;
  - keyboard first show after IME start: < 300 ms.
- CI on GitHub Actions; Conventional Commits; Keep a Changelog; releases by pushing a `vX.Y.Z` tag, which publishes signed APKs for both flavors; reproducible builds. Same model as UltimateDeck; details in `docs/CI_CD.md`.

---

## 13. Delivery plan

Overview only; the authoritative task list is `PLAN.md`. Each phase ends with a merged PR and an automatic release, and work continues without stopping (see `CLAUDE.md`).

0. Bootstrap · 1. IME core · 2. Suggestion engine · 3. Style engine · 4. Private mode · 5. Clipboard & emoji · 6. Voice core · 7. Models & flavors · 8. Polish · 9. Release 1.0 · 10. Gesture typing (1.1).

---

## 14. Acceptance criteria (1.0)

- Typing in Spanish, English and mixed text with autocorrect and predictions works reliably; performance targets in §12 met on CI benchmarks where measurable.
- At least 7 built-in presets with clearly different looks; a style exported and re-imported is identical.
- No preset, string or identifier references another keyboard product.
- `full` APK has no INTERNET permission and dictation works in airplane mode.
- Dictating a ~10-word sentence with `base` returns text in ≤ 2 s on the Find X8 Pro.
- In an incognito browser tab, private mode activates automatically and no new words are learned.
- Copied text in private mode never appears in clipboard history.
- No crashes in a 30-minute mixed typing + dictation session.
- All third-party components credited in-app and in `docs/THIRD_PARTY.md`.
