<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0022: Many languages, several at once

Status: accepted

## Context

The keyboard shipped with Spanish and English only: two layouts, two dictionaries and a suggestion engine mixing
exactly those two, built in the service. The owner asked for two things: typing in two or more languages at the
same time, and having all the languages other keyboards have.

## Decisions

1. **A data-driven catalog in a new module, `:languages`.** One `Language` per supported language: BCP-47 tag (also the
   settings key and the dictionary id), native and English name, script, the ids of its letter layouts (default first)
   and a short name for the space bar. The module has no dependencies, so `:core` (settings sanitising), `:layouts`,
   `:dictionaries`, `:ime` and `:app` can all use it, and tests of `:layouts` and `:dictionaries` check that every
   catalog entry has its layouts and its pinned dictionary. Putting it in `:layouts` was rejected because `:core`
   would then depend on the layout resources only to validate a list of tags. Regional variants (`en-US`/`en-GB`,
   `pt-BR`/`pt-PT`) are separate entries that exclude each other (they share the primary language subtag), which keeps
   the engine, keyed by language, unambiguous.
2. **Hebrew is not offered.** It is written right to left; the key rows, the composing text, the cursor movement and
   the gesture trail all assume left to right. Doing it half-way would be worse than not doing it. Serbian is offered
   in Cyrillic only, because the pinned Serbian word list is Cyrillic; a Latin layout without a dictionary would give
   no suggestions.
3. **Layouts are written from scratch** as JSON, one file per layout, from the standard national keyboard standards
   (AZERTY, QWERTZ, Turkish Q, and so on) and the accent sets of each language. Long-press alternatives cover the
   accented forms and the number-row digits as hints, like the existing layouts.
4. **Dictionaries** come from the same pinned AOSP list collection as Spanish and English (Apache-2.0); details in
   ADR 0008 (update) and `docs/THIRD_PARTY.md`.
5. **Enabled languages are settings.** `enabledLanguages` (default `es`, `en-US`, as today; at most six) and
   `languageLayouts` (a layout choice per language, only where it differs from the default). The active layout stays
   `letterLayoutId`; its language is the primary language of the suggestion engine. Everything is sanitised through the
   catalog.
6. **The engine mixes any number of languages.** `MixedSuggestionEngine` keeps its rules (per-language weights from the
   last words, primary language first, deduplicated merge, a valid word of any language is never "corrected", learning
   goes to the language that knows the word or the context points to). New: every secondary language keeps a prior floor
   (`minSecondaryPrior`, 0.1) so six languages stay usable, autocorrect is allowed when the top suggestion's language
   leads by a factor of two (a gate of 0.7 is out of reach with six languages), and a mixed engine can share its
   engines (`closeEngines = false`). A `LanguageEngineManager` in `:ime` keeps one native engine for the process and
   swaps a new mixed engine over it whenever the enabled languages, the active language or a layout choice changes;
   dictionaries are copied and built only for enabled languages, on one background thread, and a language that is
   switched off gives back its native state and its built dictionary. The key grid used to forgive slips on
   neighbouring keys comes from the language's layout (`KeyboardGeometry.fromRows(layout.letterRows())`), so AZERTY,
   Cyrillic and Greek are corrected like QWERTY.
7. **Gestures use an alphabet of every letter that has a key somewhere.** `GestureAlphabet` grew from 28 to 109 letters
   (Latin extensions, Cyrillic, Greek). A letter without a key on the layout on screen stands where its base letter's
   key is, so a German word is traced on a layout without ö. The vocabulary is streamed from the word lists and bounded
   to the 120,000 most frequent words per language; the decoder knows which language is active. Six languages decode in
   the same time as two (p95 4 ms on the JVM) because the first and last key prune the candidates.
8. **Bundling.** The 24 word lists add 24.2 MiB to the APK (measured, `docs/PERFORMANCE.md`), under the 25 MiB the owner
   set as the limit for bundling everything, so all are bundled in both flavors and no import or download of
   dictionaries exists. If lists grow past that, the same installer could fetch them with the model downloader of the
   `lite` flavor.
9. **Not covered.** Dictation stays Spanish and English (the formatter's function words and the settings are
   Spanish/English), emoji search keywords stay English and Spanish, and Serbian has no Latin-script layout (no
   dictionary). Hebrew and other right-to-left languages are out of scope (decision 2).

## Consequences

- Adding a language is a layout JSON, a pin in `sources.properties` with its `THIRD_PARTY.md` row and a line in the
  catalog; tests check the three agree and that the common words of the dictionary can be typed on the layout.
- Memory grows with the enabled languages (a dictionary, a vocabulary of up to 120,000 words); the limit of six and the
  note on the Languages screen are the guard rail.
- The word cap of 250,000 per dictionary cuts Romanian (1.1 million entries) to its frequent words.
