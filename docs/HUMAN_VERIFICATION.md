<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Pending human verification

Things that need a physical device or human judgment. Tick when done.

## Repository

- [ ] Add the release signing secrets (`docs/HUMAN_TASKS.md`); then re-run any failed Release workflow.

## Phase 1 — IME core

- [ ] Typing feel on the Find X8 Pro (key sizes, touch accuracy, long-press delay, haptics).
- [ ] Spacebar cursor drag and delete-key selection drag feel natural.
- [ ] Layouts and gestures in a few real apps (browser, messaging, notes, password field).
- [ ] Latency numbers on the Find X8 Pro (`docs/PERFORMANCE.md`, run a debug build and read `UKLatency` in logcat).

## Phase 2 — Suggestions

- [ ] Typing feel and autocorrect quality in Spanish, English and mixed text on the Find X8 Pro (plan 2.9).
- [ ] Bottom margin (settings, 0-48 dp) looks right with gesture navigation and with the three-button bar.
- [ ] First launch after install: dictionaries build in the background without freezing typing (`UltimateKeys` log tag, no native crash).
- [ ] Suggestion latency on a real device (`docs/PERFORMANCE.md`; the emulator test asserts p95 < 30 ms for engine calls only).

## Phase 6 — Voice core

- [ ] Dictation quality in Spanish, English and mixed sentences on the Find X8 Pro with a real `base` model (plan 6.9); note which language the auto-detection picks for short phrases and whether forcing a language helps.
- [ ] First use: the microphone key shows the explanation, the permission dialog appears from the transparent activity, and after granting it dictation starts by itself. After refusing once, the panel offers "Open settings" and that screen opens.
- [ ] Auto-stop after a pause feels right (1.5 s default) in a quiet room, in a street and in a car; the level meter follows the voice.
- [ ] The microphone is released when the keyboard hides or the app changes (the system's microphone indicator goes away), and during a phone call the panel shows the "microphone busy" message.
- [ ] Inserted text spacing and capitalization read naturally after a full stop, after a comma and in the middle of a sentence; one undo in the app removes a whole dictated phrase.
- [ ] In a password field and with private mode on, dictated words do not appear in the suggestion bar afterwards.
- [ ] Real-time factor and latency of `tiny` and `base` on the device (`UKVoice` log tag in a debug build); check that the threads run on the fast cores.
- [ ] Voice panel look in every built-in style, light and dark, with the microphone in the bottom row and in the suggestion bar.

## Phase 5 — Clipboard and emoji

- [ ] Copy text in a few apps (browser, messaging, notes): it appears in the clipboard panel; tapping it inserts it; pin, delete and clear all behave.
- [ ] Copy from a password manager (sensitive flag, Android 13+): nothing is stored. Copy while private mode is on: nothing is stored.
- [ ] Retention: with 1 hour selected, clips older than an hour disappear when the panel opens; pinned clips stay.
- [ ] Emoji panel on the Find X8 Pro: scrolling is smooth, emoji newer than the device's emoji font are hidden (no empty boxes), skin tone button cycles and applies to hands and people.
- [ ] Emoji search with the on-panel keys in English and Spanish ("corazon", "heart", "españa"); feel of the small search keyboard.
- [ ] Panel look in each preset, light and dark, and the open/close transition (none, fade, slide) with the three-button and gesture navigation bars.
- [ ] Backspace on the panel bar repeats while held.
- [ ] Panels are exclusive: opening emoji or clipboard while dictating cancels the dictation; the mic key closes an open panel. With the style showing tool icons and the mic in the suggestion bar, the strip shows private toggle, clipboard, emoji and mic without crowding the suggestions.
