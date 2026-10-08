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

## Phase 8 — Accessibility, translation, licenses

- [ ] TalkBack on the keyboard (Settings, Accessibility, TalkBack on): touching a key says its name (letters in the case they will be typed), a double tap types it, "activate on lift" works if enabled; swipe right/left moves through the suggestion bar and then the key rows in reading order; there is no key announced twice and no empty "Keyboard" stop that does nothing.
- [ ] TalkBack: shift says "off / on, next letter in capitals / caps lock on" and changes as you double tap it; enter says the editor action (Go, Search, Send, Next, Done); the globe key's actions menu offers "Choose keyboard"; keys with long-press alternatives list them in the actions menu and typing one inserts it.
- [ ] TalkBack: private mode button reads its state; clipboard, emoji and microphone buttons in the bar are announced and open their panels; with a panel open only the panel is explorable; the microphone panel reads "Listening", the hint and the errors, and the round button stops dictation.
- [ ] TalkBack in the emoji panel (category buttons say "selected", skin tone says its current tone, cells say the emoji), the clipboard panel (paste, pin and delete are separate stops, the pinned state is spoken) and the backspace button (a double tap deletes one character).
- [ ] TalkBack on the app screens: home, styles, style editor (section headers say expanded/collapsed, sliders read "label: value", switches toggle on a double tap anywhere on the row), my dictionary and the licenses screen; focus order follows the visual order.
- [ ] Switch Access or Voice Access ("tap q", "tap shift") can press keys; with large font (200%) and display size the app screens still fit and nothing is clipped.
- [ ] Typing feel with TalkBack off is unchanged (no extra latency): compare `docs/PERFORMANCE.md` figures before and after on the Find X8 Pro.
- [ ] Spanish: switch the phone to Spanish and read every screen of the app and every panel of the keyboard for wording that sounds unnatural or is cut off.
- [ ] Licenses screen: every link opens, the GPL, Apache, MIT, SIL OFL and Unicode texts scroll and are readable in light and dark.

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

## Phase 7 — Models and flavors

- [ ] `full` on a clean install: open the keyboard, tap the microphone and dictate a sentence in Spanish and one in English with no network at all (airplane mode). The first dictation right after install may report "no model" for a second while the bundled model is copied (check how long it takes on the Find X8 Pro).
- [ ] `full`: Models screen lists Base as in use; delete it, dictate (panel says there is no model, "Choose a model" opens the manager), press "Install the bundled model" and dictate again.
- [ ] `lite` on a clean install: the panel's "Choose a model" opens the manager; download Base on Wi-Fi: progress moves, the file is usable afterwards. Repeat with Small and switch between them with "Use".
- [ ] `lite` resume: start the Small download, turn Wi-Fi off in the middle (status says waiting), turn it on: it continues from where it stopped (not from 0). Force-stop the app in the middle and reopen: same.
- [ ] `lite` Wi-Fi only: with the switch on and only mobile data, the download waits; with the switch off it runs on mobile data. Cancel removes the partial download (storage in Settings drops back).
- [ ] Import: pick a real `ggml-*.bin` from storage (Downloads, an SD card or a cloud provider through the picker): a catalog file installs without a warning, another valid Whisper file asks for confirmation and shows its size and checksum, a text file or a photo is rejected with a clear message. Cancel at the warning leaves nothing installed.
- [ ] Dictation settings: language "Spanish" and "English" force the language even when speaking the other one; "Automatic" picks per utterance; changing the pause length changes when the dictation stops by itself.
- [ ] Both screens in light and dark, large font size and a narrow screen: nothing is cut off; Spanish and English texts read well.
- [ ] Check the merged APKs once with `aapt2 dump permissions`: `full` has no INTERNET or ACCESS_NETWORK_STATE; `lite` has INTERNET.

## Settings redesign (app screens)

- [ ] On Android 15 or later: open every app screen (home, each sub-screen, styles gallery and editor, dictation settings and models, my dictionary, licenses). No title or button sits under the status bar and the last row is not under the gesture bar; rotate to landscape on a phone with a camera cutout.
- [ ] Type in a text field of a screen (Setup "Try it here", my dictionary, a style name or a color code): the field stays visible above the keyboard.
- [ ] Switch the phone between light and dark, and change the wallpaper colors: the screens follow, text stays readable and the selected segment of a choice is easy to see.
- [ ] TalkBack: the home rows read as "Typing, button" with their state ("Suggestions, On"); a switch row reads once with its state; a slider row reads its label and value ("Keyboard height, 100%") and adjusts with swipe up and down; headings can be jumped to.
- [ ] Largest font size: no row is cut off; segmented choices wrap their labels; Spanish texts read well.
- [ ] Back: the system back and the bar's back button return one screen at a time (sub-screen, then home, then leave); in the style editor, back from a group returns to the overview and from the overview leaves the editor.

## Phase 9 — Acceptance criteria of SPEC.md section 14 (see `docs/ACCEPTANCE.md`)

- [ ] Airplane mode: with the `full` APK and airplane mode on, dictate a sentence in Spanish and one in English; both are inserted.
- [ ] Dictating a sentence of about 10 words with `base` returns text in 2 s or less on the Find X8 Pro (note the time and the sentence in `docs/PERFORMANCE.md`).
- [ ] In an incognito tab of a real browser, private mode turns on by itself (the strip icon is filled and tinted); type a made-up word several times, leave the tab, and check it is never suggested.
- [ ] A 30-minute session mixing typing in Spanish and English, gestures and dictation, with no crash and no stuck keyboard (also on a Pixel).
- [ ] Time from tapping a field to the keyboard being drawn is under 300 ms (cold and warm), on the Find X8 Pro.
- [ ] Before the `1.0.0` tag: read the store texts in `fastlane/metadata/android/*/` and the README against the build you are about to release (flavors, models, the licenses screen), and look at the screenshots there.
- [ ] After the `1.0.0` tag: build the `lite` APK on another machine and compare it with the published one (`docs/DISTRIBUTION.md`), then open the fdroiddata merge request.

## Phase 8, performance

- [ ] On the Find X8 Pro, run a debug build and read `UKLatency` in logcat: `first-show` (target under 300 ms), `key-to-commit` p95 (under 16 ms), gesture `lift-to-candidates` (under 50 ms); add the numbers to the table in `docs/PERFORMANCE.md`.
- [ ] Dictate once, wait two minutes and check the app's memory drops (the speech model is freed), then dictate again and check the model comes back without an error.
