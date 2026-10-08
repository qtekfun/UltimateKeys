<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org).

## [Unreleased]

## [0.5.0] - 2026-10-08

## [0.4.0] - 2026-10-08

### Added

- Offline dictation (core): a microphone key (bottom row or suggestion bar, as the style chooses) opens a voice panel with a listening animation and a level meter, stops by itself after a pause, then writes the text at the cursor as a single undo step with the right spacing and capital letters. Speech is recognised on the device by whisper.cpp, in Spanish or English (detected per phrase, or forced); sound is kept in memory only and nothing dictated is learned in private mode. The model is loaded from a local file for now; the model manager arrives in a later release.
- Private mode: turns on by itself in incognito tabs and password fields, or by hand with the button at the left of the suggestion bar. While it is on nothing is learned (no history, no new words), the bar is tinted by the style and shows an icon. Setting: whether a manual switch-on ends when the keyboard closes.

### Added

- Keyboard styles: seven original built-in styles (Ultimate, Soft, Flat, Outline, Classic, Midnight, Paper), each with light and dark colors, bundled open fonts and Material You colors. A style editor with a live preview, contrast warnings, duplicate and reset, plus import, export and sharing of `.ukstyle` files.
- Bottom-row arrangements and a suggestion-bar layout chosen by the style.

## [0.3.0] - 2026-10-08

### Changed

- New launcher icon: an adaptive icon with a keyboard glyph, plus a monochrome (themed) version.

### Added

- Bottom margin below the keys, adjustable in settings (0-48 dp), and the keyboard no longer draws under the system navigation bar.
- Suggestion strip with up to three words, auto-correction on space and punctuation (backspace right after restores what you typed) and next-word prediction. Both can be turned off in settings.

## [0.2.0] - 2026-10-07

The keyboard can now be used to type, without suggestions yet.

### Added

- The keyboard now types: Spanish and English QWERTY layouts (with `ñ`), two symbol pages, numeric and phone layouts, and an optional number row.
- Long-press accents and alternatives with slide-to-select; shift, caps lock and automatic capitalization; double space inserts a period.
- Delete key with accelerating repeat; drag left on delete to select text and release to remove it; drag or hold on the space bar to move the cursor.
- Editor actions on the Enter key (Go, Search, Send, Next, Done) matching the field.
- Settings: keyboard height, number row, long-press delay, vibration, key sound, auto-capitalization, double-space period and smart punctuation spacing.
- A setup screen that helps you enable and select the keyboard.

## [0.1.0] - 2026-10-07

First build of the project: an empty keyboard app with the full build, test and release pipeline. It cannot be used to type yet.

### Added

- Two builds: `full` (no network permission) and `lite`.

[Unreleased]: https://github.com/qtekfun/UltimateKeys/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/qtekfun/UltimateKeys/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/qtekfun/UltimateKeys/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/qtekfun/UltimateKeys/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/qtekfun/UltimateKeys/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/qtekfun/UltimateKeys/releases/tag/v0.1.0
