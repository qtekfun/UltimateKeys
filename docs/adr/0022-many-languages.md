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
