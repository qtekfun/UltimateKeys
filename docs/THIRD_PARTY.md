<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Third-party components

| Component | Use | License | Status |
|---|---|---|---|
| AOSP LatinIME native engine | Suggestions and autocorrect | Apache-2.0 | Planned (Phase 2), vendored in `third_party/aosp-latinime/` |
| whisper.cpp (with its bundled ggml) | Offline speech-to-text | MIT | Phase 6, git submodule `third_party/whisper.cpp`, unmodified (see below) |
| Whisper models `base`, `small` | Dictation models (data, never committed) | MIT | Phase 7, bundled (`base`, `full`) or downloaded (`lite`) with SHA-256, see `docs/MODELS.md` |
| AOSP LatinIME word lists `es`, `en` | Dictionaries for the engine | Apache-2.0 | Phase 2, fetched at build time with SHA-256 (see below) |
| Unicode CLDR annotations and emoji list | Emoji search keywords, categories and skin-tone forms | Unicode License v3 | Phase 5, processed at build time (see below) |
| Open fonts and Material Symbols | Style engine | OFL / Apache-2.0 | Planned (Phase 3) |

Libraries from Gradle (AndroidX, Kotlin, kotlinx, Room) are listed by their licenses in the in-app licenses screen. That screen reads `app/src/main/assets/licenses/components.json`; when you add a row to the component or font tables (or a library to `gradle/libs.versions.toml`), add the matching entry there, with the row's text in `thirdPartyRows`: `LicenseCatalogTest` fails until you do (ADR 0018).

## Dictionaries (`:dictionaries`)

Pins live in `dictionaries/sources.properties`. The Gradle task `:dictionaries:fetchDictionaries` downloads each
file from the pinned commit, verifies its SHA-256 and caches it under `$GRADLE_USER_HOME/ultimatekeys-dictionaries/`
(no network when the verified cache entry exists). Format and rationale: `docs/adr/0008-dictionary-format.md`.

| Language | File | Source (pinned) | SHA-256 | Blob id | License |
|---|---|---|---|---|---|
| Spanish (`es`) | `dictionaries/es_wordlist.combined.gz` | `https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/127336e9f29d69607eab55982324b210279ae8c5/dictionaries/es_wordlist.combined.gz` | `889ad52bce2933e2a30a0560b8d5a76f5334500e776a7db7c7cb3e9e79fc2652` | `71e7309fc39fce01f26080afea2fa6d689bc20a3` | Apache-2.0 |
| English (`en`, US spelling) | `dictionaries/en_US_wordlist.combined.gz` | same repository and commit | `0f78dd455b532be169a23f233227b811fabced4b5bd7fc9c40cc05839793bcbd` | `8aed9c5e0b94d0f3e9654f66104d7af1e84c454f` | Apache-2.0 |

Pinned commit: `127336e9f29d69607eab55982324b210279ae8c5` (tip of `main` checked on 2026-10-07, dated 2025-02-26).
Both files are unigram lists with frequencies 0..255 (about 236k Spanish and 156k English headwords) whose header
reads `dictionary=main:es,locale=es,...,version=54`.

Attribution: Copyright (c) 2008, The Android Open Source Project, licensed under the Apache License, Version 2.0.
The repository `NOTICE` at the pinned commit states this for the whole project, and the `dictionaries/` directory
carries no separate license file. The license text ships with the engine under `third_party/aosp-latinime/LICENSE`.

Why this is acceptable: Apache-2.0 may be combined into a GPL-3.0 work and F-Droid accepts it. Residual risk: the
lists have no per-file provenance and the repository does not document the underlying corpus, so we rely on the
project-wide Apache-2.0 grant.

### Alternatives considered (not used)

These notes come from general knowledge and were not re-verified by reading each license text, because none of
these sources is used.

| Candidate | Decision | Reason |
|---|---|---|
| Hunspell/LibreOffice `es_ES` and `en_US` dictionaries | Not used for now | Licenses differ per dictionary and must be read file by file. They carry no word frequencies, so suggestions would rank poorly. Possible later source for validity checking only. |
| Frequency lists derived from OpenSubtitles (for example FrequencyWords) | Rejected | The corpus's redistribution terms are unclear, and a license on the derived list does not clear the underlying data. |
| Wiktionary-derived frequency lists | Rejected | Share-alike (CC BY-SA) terms are not clearly compatible with shipping inside a GPL-3.0 binary. |

## Emoji data (`:emoji`)

Pins live in `emoji/sources.properties`. The Gradle task `:emoji:generateEmojiData` downloads each file, verifies its
SHA-256, caches it under `$GRADLE_USER_HOME/ultimatekeys-emoji/` (no network when the verified file is cached) and
writes the compact assets `emoji/catalog.txt` and `emoji/search_<lang>.tsv` (format and rationale:
`docs/adr/0012-clipboard-and-emoji.md`). The processing code is unit-tested in `build-logic`.

| Data | Source (pinned) | SHA-256 | License |
|---|---|---|---|
| Emoji list: groups, order, skin-tone forms | `https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt` (Unicode emoji 17.0; versioned directories are immutable) | `1d8a944f88d7952f7ef7c5167fef3c67995bcae24543949710231b03a201acda` | Unicode License v3 |
| CLDR annotations `en` | `unicode-org/cldr-json` at commit `91c267402229a59e3ef2774544f001bf959e8809` (tag 48.2.3), `cldr-json/cldr-annotations-full/annotations/en/annotations.json` | `f22083cb86dffb63a643d5bacb5d9899f82d2fa5d388ad4f3aed72184acef505` | Unicode License v3 |
| CLDR annotations `es` | same commit, `.../annotations/es/annotations.json` | `339cdf9ae5fe6d3250d3c53b95ff6d8a4dae96c7c0d279756ee7a73c43c84b56` | Unicode License v3 |
| CLDR derived annotations `en` (flags, hair, other sequences) | same commit, `cldr-json/cldr-annotations-derived-full/annotationsDerived/en/annotations.json` | `15c9457afa703a32dcc78d54626418a91e820fe0ddd67b7d491c4c4d9f5846ab` | Unicode License v3 |
| CLDR derived annotations `es` | same commit, `.../annotationsDerived/es/annotations.json` | `f9f43c327c5b76dbd337496738c0f5c526ee1c803dd683d60100b5fce85ab79e` | Unicode License v3 |

Attribution: Copyright (c) 1991-2025 Unicode, Inc. Licensed under the Unicode License v3
(`https://www.unicode.org/license.txt`, also the `LICENSE` file of `cldr-json` at the pinned commit). The license is
permissive (use, copy, modify, distribute, sell, provided the notice and permission text accompany copies), is
approved by the OSI, and is compatible with GPL-3.0 and accepted by F-Droid. The generated assets keep names and
keywords unmodified apart from dropping duplicates and keywords that equal the name; the license text ships with the
app in the licenses screen (Phase 8). Emoji glyphs are drawn by the device's own emoji font; no emoji artwork is
bundled.

Room (`androidx.room`, Apache-2.0) stores the clipboard history, with KSP (`com.google.devtools.ksp`, Apache-2.0) as
its annotation processor (no kapt, see ADR 0006).

## Fonts and icons (style engine)

All fonts are bundled in `ime/src/main/assets/fonts/` unmodified, each with its licence text (`OFL-*.txt`). They come from the `google/fonts` repository at commit `5e8a3ba899557829a76cfdac30fa512bda91d7ca`.

| Font | File | Licence | SHA-256 |
|---|---|---|---|
| Inter (variable) | `Inter-Variable.ttf` | SIL OFL 1.1 | `29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031` |
| Roboto Flex (variable) | `RobotoFlex-Variable.ttf` | SIL OFL 1.1 | `9b523f7d82593df0107173849ebb8c817471a1df4b4fb2c3cbf40cfd810c8281` |
| Atkinson Hyperlegible Regular | `AtkinsonHyperlegible-Regular.ttf` | SIL OFL 1.1 | `7fb917c89019896d0b52ee84b7cbb3304c18cb90b19a62f5e32712bd23e97669` |
| Atkinson Hyperlegible Bold | `AtkinsonHyperlegible-Bold.ttf` | SIL OFL 1.1 | `5a3b0c8cc8ca545155150b4512a1fa248298df121c50d6557e651e61fbdab92f` |
| Nunito (variable, rounded) | `Nunito-Variable.ttf` | SIL OFL 1.1 | `bb55a5ca5c2042335b3991af27c4d0705d0ef41cac6164ac737fd8f2a1e85207` |

Material Symbols (outlined, 24 px Android vectors, Apache License 2.0) from `google/material-design-icons` at commit `737e3324305806514d7909874fa1818ae1808232`, stored as `ime/src/main/res/drawable/ms_*.xml`: mic, content_paste, sentiment_satisfied, settings, more_horiz, close, visibility_off, language, backspace, keyboard_return, keyboard_hide, palette.

## whisper.cpp (`:voice`)

- Source: `https://github.com/ggml-org/whisper.cpp`, git submodule at `third_party/whisper.cpp`, pinned to the release tag
  `v1.9.5` (commit `d1be6fde11ac6e0407606b4e42fe72d34add8037`, the latest stable release on 2026-10-08). It includes its own
  copy of ggml. License: MIT ("Copyright (c) 2023-2026 The ggml authors"), compatible with GPL-3.0-or-later and F-Droid.
- Unmodified: our code is only `voice/src/main/cpp/uk_whisper_jni.cpp` and `voice/src/main/cpp/CMakeLists.txt`, which build
  the submodule with the CPU backend only (no GPU, no OpenMP, no dynamic backends), `-O3`, NEON on ARM and deterministic
  flags. Because nothing is changed there is no `MODIFICATIONS.md`; if a patch is ever needed it must be recorded in this
  section and in `third_party/whisper.cpp.MODIFICATIONS.md`.
- Updating the pin: fetch the tags inside the submodule, check out the new release tag, commit the new submodule pointer,
  update this section and run the whisper JNI emulator test.
- The release APKs ship the compiled library only; the MIT license text is `third_party/whisper.cpp/LICENSE` and is listed
  in the in-app licenses screen (Phase 8).

### Test model and sample (CI only, never committed)

| File | Use | Source (pinned) | SHA-256 | License |
|---|---|---|---|---|
| `ggml-tiny-q5_1.bin` (about 32 MB) | Emulator JNI smoke test of `:voice` | `https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-tiny-q5_1.bin` | `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7` | MIT (OpenAI Whisper weights converted by the whisper.cpp project; the repository card states `license: mit`) |
| `samples/jfk.wav` (352 KB) | Audio the smoke test transcribes | Inside the submodule at the pinned tag | n/a | Speech excerpt shipped by whisper.cpp for its own tests |

`.github/workflows/ci.yml` downloads the model, verifies the checksum and caches it under the key
`whisper-tiny-q5_1-<sha256>`. The production models (`base`, `small`) and their pins are in `docs/MODELS.md` and
`voice-models/src/main/assets/models.json` (same repository and commit; MIT). The `full` flavor bundles `base`; the `lite`
flavor downloads either. Neither file is ever committed.
## Papers behind gesture typing

The `:gesture` module (Phase 10) is original code. These papers were read for their ideas and are cited in `docs/gesture/DESIGN.md`; none of their code or data is used.

- S. Zhai and P.-O. Kristensson, "Shorthand writing on stylus keyboard", CHI 2003.
- P.-O. Kristensson and S. Zhai, "SHARK2: a large vocabulary shorthand writing system for pen-based computers", UIST 2004.
- P.-O. Kristensson and S. Zhai, "Relaxing stylus typing precision by geometric pattern matching", IUI 2005.
- S. Zhai and P.-O. Kristensson, "The word-gesture keyboard: reimagining keyboard interaction", Communications of the ACM 55(9), 2012.
- J. O. Wobbrock, A. D. Wilson and Y. Li, "Gestures without libraries, toolkits or training: a $1 recognizer for user interface prototypes", UIST 2007.
- G. M. Chaikin, "An algorithm for high-speed curve generation", Computer Graphics and Image Processing 3(4), 1974.

The word frequencies used by gesture typing are the ones of the pinned word lists listed above; no new data source was added.
