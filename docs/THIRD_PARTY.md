<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Third-party components

| Component | Use | License | Status |
|---|---|---|---|
| AOSP LatinIME native engine | Suggestions and autocorrect | Apache-2.0 | Planned (Phase 2), vendored in `third_party/aosp-latinime/` |
| whisper.cpp (with its bundled ggml) | Offline speech-to-text | MIT | Phase 6, git submodule `third_party/whisper.cpp`, unmodified (see below) |
| AOSP LatinIME word lists `es`, `en` | Dictionaries for the engine | Apache-2.0 | Phase 2, fetched at build time with SHA-256 (see below) |
| Unicode CLDR annotations | Emoji search keywords | Unicode License | Planned (Phase 5) |
| Open fonts and Material Symbols | Style engine | OFL / Apache-2.0 | Planned (Phase 3) |

Libraries from Gradle (AndroidX, Kotlin, kotlinx, JUnit) are listed by their licenses in the in-app licenses screen (Phase 8).

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
`whisper-tiny-q5_1-<sha256>`. The production models (`base`, `small`) and their pins arrive with Phase 7.
