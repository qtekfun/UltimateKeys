<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0020: Gesture typing

Status: accepted

## Context

SPEC section 5 and plan Phase 10 add word-gesture typing as a `:gesture` module: SHARK2-style template
matching combined with the engine's language model and the mixed Spanish/English weighting. The algorithm and
numbers are in `docs/gesture/DESIGN.md` and `docs/gesture/RESULTS.md`. The decisions below are the ones the
spec did not settle.

## Decisions

1. **`:gesture` is pure Kotlin with no dependency.** It knows key rectangles (`GestureKey`), points, and words
   with a frequency; `:ime` adapts `KeyGeometry` and the dictionaries to it. This keeps the algorithm testable
   on the JVM and the module out of any Android or engine change. The evaluation harness (synthetic
   gestures) lives in `:gesture` main code, not in tests, so that `:ime` tests (which have the real layouts and
   word lists) and any later tool can run it; the app never calls it.
2. **The vocabulary is read from the word lists, not asked of the native engine.** The AOSP engine exposes
   suggestions for a typed prefix, not an enumeration with frequencies. The same pinned lists build the
   engine, so the 0..255 frequencies are identical to its unigram scores. Words under frequency 30 and
   offensive words are left out (memory, speed). The engine is still used for what it knows beyond the lists:
   next-word predictions and the person's own words.
3. **Mixed-language weighting is re-implemented over the vocabulary (`LanguagePrior`)**, with the formula and
   defaults of `MixedSuggestionEngine`, instead of calling `languageWeights`: the engine is swapped in
   behind `SharedEngine` after a slow build, and gestures must work as soon as the lists are parsed. The two
   must be kept in step if the mixed model changes.
4. **Style schema version 2.** The trail colour is a palette field (`gestureTrail`), not a fixed colour, so
   styles control it like every other colour. Version 1 files are migrated: the key action colour becomes the
   trail and the private mode tint, which version 1 files written before Phase 4 lack, gets its default.
   Presets set the trail explicitly (the action colour, except Flat light where it would not stand out).
5. **A gesture word is learned late.** The engine's `learn` is called when the word stays (next edit or end
   of input), not at commit, so words that are swapped for an alternative or taken back with backspace never
   pollute the history. It goes through the same hook, `learningAllowed` and `PrivacyGuardedEngine` as typed
   words, so private mode blocks it.
6. **Commit behaviour.** The word is committed text (not composing), followed by a space that punctuation
   swallows; a leading space is added only after a letter, digit or punctuation. Backspace right after takes
   back the whole insertion if the text before the cursor is still exactly what was inserted. The strip's
   three slots become `[alternative, typed word, alternative]` and a tap on an alternative replaces the word
   in place.
7. **A touch is a gesture after about one key of travel, scaled by the sensitivity setting** (1.8 to 0.55 key
   widths). Shorter paths that end as a gesture are typed as the key press they were, so a slide to a
   neighbouring key never loses a letter. While one finger glides, other fingers are ignored.
8. **Decoding runs on the suggestion dispatcher** (single-threaded, where the engine may be called) and the
   result is committed on the main thread. Gestures are ignored until the vocabulary is installed (a second
   or two after the first dictionary install), and while the setting is off or the page is not the letters.
9. **Documents are numbered 0020** to leave 0009 to 0019 to the other Phase 5 to 9 work running in parallel.

## Consequences

* About 10 MB of heap for the vocabulary while the keyboard process lives.
* Accuracy numbers depend on a synthetic noise model; real feel is a human check (plan 10.8).
* `SuggestionHook` gained `onGestureCommitted` (default no-op) and `SuggestionState` a `gesture` flag.
* Any code that builds a `Palette` must now give `gestureTrail`.
