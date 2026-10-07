<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Third-party components

| Component | Use | License | Status |
|---|---|---|---|
| AOSP LatinIME native engine | Suggestions and autocorrect | Apache-2.0 | Planned (Phase 2), vendored in `third_party/aosp-latinime/` |
| whisper.cpp | Offline speech-to-text | MIT | Planned (Phase 6), git submodule |
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
