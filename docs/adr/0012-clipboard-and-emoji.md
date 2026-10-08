<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0012: Clipboard history and emoji data

Status: accepted

## Context

Phase 5 adds a clipboard history (Room), a clipboard panel, an emoji panel and offline emoji search in English and
Spanish from CLDR annotations. SPEC section 9 forbids storing clips in private mode and clips flagged sensitive.

## Decisions

### Clipboard

1. **Rules are separate from storage.** `ClipboardHistory` holds every rule (enabled, private mode, sensitive flag,
   blank or oversized text, retention, item limit) over a `ClipStore` interface. Room backs it in the app
   (`RoomClipStore`, schema exported to `clipboard/schemas`); `MemoryClipStore` backs the unit tests. The Room
   queries are tested against real SQLite through Robolectric.
2. **Private mode is checked at the moment of the copy**, by the same `PrivacyState` the suggestions use, so a
   manual switch-on and every automatic reason are covered. The sensitive flag is read from the clip description
   extras (`android.content.extra.IS_SENSITIVE`, the literal value of `ClipDescription.EXTRA_IS_SENSITIVE`, so it
   also works below Android 13) before the content is read.
3. **Pinned clips** never expire and do not count against the maximum; **clear all** removes everything that is not
   pinned (pinning is an explicit "keep this"). Re-copying a stored text moves it to the top and keeps its pin.
4. **Defaults:** history on, retention 1 day, 50 clips (choices 1 h / 1 day / 7 days / forever; 5 to 500 clips).
   Texts over 20 000 characters are not stored. Only plain text is read; images and URIs are ignored.
5. **Settings** live in `KeyboardSettings` (DataStore) as the retention id string, so `:core` does not depend on
   `:clipboard`.
6. **Dependencies:** Room 2.8.5 (Apache-2.0, AndroidX) and KSP 2.3.12 (Apache-2.0, as Room's processor; no kapt).

### Emoji

1. **Sources.** Emoji groups, order and skin-tone forms come from Unicode `emoji-test.txt` 17.0; names and
   keywords from CLDR 48.2.3 annotations (`annotations-full` and `annotations-derived-full`, the latter carries the
   flags, hair variants and other sequences). Each file is pinned by URL (commit for CLDR) and SHA-256 in
   `emoji/sources.properties`; license in `docs/THIRD_PARTY.md`.
2. **Build-time processing.** `:emoji:generateEmojiData` (task and parser in `build-logic`, unit-tested there)
   writes two compact asset kinds: `catalog.txt` (`@group` headers, one base emoji per line followed by its
   single-tone variants) and `search_<lang>.tsv` (`emoji TAB name TAB keyword|keyword`). Only fully-qualified emoji
   of the nine keyboard groups are kept; two-tone combinations are not offered. About 240 KB uncompressed.
3. **Runtime index.** `EmojiSearchIndex` builds a sorted token table at load (accent-stripped with NFD, lower case)
   and answers by binary-searching prefix ranges. Every query word must match; a name word beats a keyword, a whole
   word beats a prefix, a name that starts with or equals the query gets a bonus, ties keep the panel's order.
   `EmojiSearch` queries both languages at once because the keyboard is bilingual.
4. **Normalization lives only at runtime** (one implementation for the data and the query), which is why the build
   output keeps the original spelling.
5. **Newer emoji than the device font** are hidden using `Paint.hasGlyph` when the data loads (off the main thread).
6. **Skin tone** is one preference applied to every emoji that has tone forms (no per-emoji long press yet).
   **Recents** (32) are saved in settings and are not recorded in private mode (it is a form of learning).

### Panels

1. The Compose panels live in `:ime` next to the style runtime (`ime/panels`), not in `:clipboard` and `:emoji`:
   they need `SurfaceStyle`, `DynamicPalette` and `FontCatalog`, and those modules must stay free of UI so the data
   and rules can be tested on the JVM. `:clipboard` and `:emoji` keep the data, rules and search.
2. `PanelsController` holds all panel state and decisions and is unit-tested; the composables are stateless over
   small UI-state classes and are covered by screenshot tests (`PanelScreenshotTest`) in four presets, light and dark.
3. Panels take colours (`panelBackground`, `panelSurface`, `panelText`, accent), the corner radius, the font and the
   transition from the active style. They replace the keys in the same area, so the keyboard height does not change.
4. Emoji search types into the panel itself with a small letter keyboard built from the active layout, because the
   panel replaces the keys and the editor must keep its own text.
5. The suggestion bar gets two more buttons (clipboard, emoji) after the private-mode button when the style shows
   tool icons (`ToolIcons.SHOWN`); the bottom-row emoji key opens the same panel.
