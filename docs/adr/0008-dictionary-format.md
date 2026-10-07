<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0008: Dictionary sources and format

Status: accepted (word-list part), proposed (binary conversion part)

## Context

The vendored AOSP native engine opens a binary dictionary file by path. We need Spanish and English dictionaries
with a license compatible with GPL-3.0 and F-Droid, reproducible to fetch, and loadable from the app.

## Decision

1. **Source.** Use the plain-text word lists (`*_wordlist.combined.gz`, unigram frequencies 0..255) published in
   the AOSP LatinIME repository at the pinned commit in `dictionaries/sources.properties`: `es_wordlist` for
   Spanish and `en_US_wordlist` for English. License findings and alternatives are in `docs/THIRD_PARTY.md`.
2. **Fetching.** `:dictionaries:fetchDictionaries` downloads each file from the pinned commit, verifies its SHA-256
   and caches it in `$GRADLE_USER_HOME/ultimatekeys-dictionaries/<sha256>.bin`. A verified cached file means no
   network access. The output is a byte-for-byte copy, so the build is deterministic. It is wired as a generated
   assets directory of every variant. For F-Droid, run `./gradlew :dictionaries:fetchDictionaries` in the
   recipe's `prebuild` (network allowed there) so the actual build can run offline.
3. **Runtime.** `DictionaryInstaller` copies the assets to `filesDir/dictionaries` once per `dataVersion`
   (marker file, SHA-256 re-check, staged atomic rename). `DictionaryLocator.fileFor(locale)` returns the path the
   engine opens. `DictionaryLocator.format` says what the file is, so the engine glue can refuse a format it cannot
   open.
4. **Binary conversion (not done here).** The engine reads the AOSP binary trie format, not the text list. The
   assets therefore currently have format `aosp-combined-gz`. The conversion belongs to a later step, with these
   options in order of preference:
   - Run AOSP's own converter on the pinned text lists at build time. The pinned tree contains
     `native/dicttoolkit` (C++, can be built with the NDK/CMake like the engine) and `tools/dicttool` (Java).
     It would be vendored under `third_party/` with the engine and the generated binary files published as the
     assets instead of the text lists; `format` in `sources.properties` changes to the binary format name and
     `dataVersion` is bumped.
   - Write our own writer in Kotlin/JVM. Rejected for now: the on-disk format is only documented by the reference
     implementation's sources, which we have not been able to verify a writer against without running the engine.
     If attempted later it must be validated by round-tripping every word through the native `isValidWord` in an
     emulator test (plan task 2.8).
   The `WordListParser` in `:dictionaries` is the reader for the text lists (tests, tooling, a possible fallback
   word check).

## Consequences

- No dictionary data is committed to the repository (about 2 MB compressed per language is downloaded and cached).
- Changing a source means editing `sources.properties`, bumping `dataVersion`, and updating `docs/THIRD_PARTY.md`.
- The lists carry only unigram frequencies: next-word prediction depends on user history until a bigram source with
  a verified license is found.
- The Spanish list marks 195 words `possibly_offensive` and flags such as `abbreviation`; the converter step
  decides how those map to engine flags (for example, hide offensive words from suggestions by default).

## Update: on-device building rejected, pre-build with AOSP's dictionary tool

First attempt: build the main dictionaries on the first run by adding every word to a native updatable dictionary (`DictionaryBuilder`). Measured on a Pixel 8: the Spanish build stops after one word and the English build crashes the process with SIGSEGV near 40,000 words (`DynamicPtReadingUtils::getParentPtNodePosOffset`, "buffer overflow after secondary allocation"). A native crash takes the keyboard down, so this path is not used (`ENGINE_ENABLED = false` until the new one lands) and the builder stays only as a tested helper.

Decision: produce the read-only binary dictionaries at build time with AOSP's own `dicttoolkit` (C++, same upstream commit as the engine, vendored under `third_party/aosp-latinime/dicttoolkit`), built for the host by a Gradle task and run on the pinned word lists. The read-only mmap path is the one AOSP has used for years. The build needs a host C++ toolchain and CMake (present on CI; F-Droid adds them in `sudo`).
