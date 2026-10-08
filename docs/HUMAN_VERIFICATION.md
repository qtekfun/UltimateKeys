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
## Phase 10 — Gesture typing

- [ ] Real gesture typing feel on the Find X8 Pro (plan 10.8): the distance before a swipe becomes a gesture (slider "Gesture sensitivity"), the trail, accuracy on everyday Spanish and English words, short words, and gliding right after typing a word by hand.
- [ ] Gesture candidates latency on the phone: debug build, read `gesture lift-to-candidates` under the `UKLatency` tag (target under 50 ms; `docs/gesture/RESULTS.md` has the desktop figure).
- [ ] Gesture typing does not leak into private mode: glide a word in an incognito field or with private mode on, then check the word is not suggested later.
- [ ] Gesture typing on both letter layouts (Spanish and English), the number row on, and a 70% / 130% keyboard height.
- [ ] The first launch after install: gestures start working a moment after typing does (the word lists are read in the background).

## Phase 9 — Acceptance criteria of SPEC.md section 14 (see `docs/ACCEPTANCE.md`)

- [ ] Airplane mode: with the `full` APK and airplane mode on, dictate a sentence in Spanish and one in English; both are inserted.
- [ ] Dictating a sentence of about 10 words with `base` returns text in 2 s or less on the Find X8 Pro (note the time and the sentence in `docs/PERFORMANCE.md`).
- [ ] In an incognito tab of a real browser, private mode turns on by itself (the strip icon is filled and tinted); type a made-up word several times, leave the tab, and check it is never suggested.
- [ ] A 30-minute session mixing typing in Spanish and English, gestures and dictation, with no crash and no stuck keyboard (also on a Pixel).
- [ ] Time from tapping a field to the keyboard being drawn is under 300 ms (cold and warm), on the Find X8 Pro.
- [ ] Before the `1.0.0` tag: read the store texts in `fastlane/metadata/android/*/` and the README against the build you are about to release (flavors, models, the licenses screen), and look at the screenshots there.
- [ ] After the `1.0.0` tag: build the `lite` APK on another machine and compare it with the published one (`docs/DISTRIBUTION.md`), then open the fdroiddata merge request.
