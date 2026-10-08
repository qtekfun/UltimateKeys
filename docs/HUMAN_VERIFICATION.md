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
