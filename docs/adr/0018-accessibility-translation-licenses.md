<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0018: Accessibility, translation checks and the licenses screen

Status: accepted

## Context

Plan Phase 8 asks for TalkBack support on the keys, panels and settings (8.3), a complete Spanish
translation (8.4), an in-app open-source licenses screen built from `docs/THIRD_PARTY.md` (8.5), and zero
lint warnings with the coverage targets met (8.6). The key surface is one `Canvas` (ADR 0005) for typing
latency, so a screen reader sees no keys.

## Decisions

1. **Virtual key nodes exist only while an accessibility service runs.** `KeyboardSurface` composes a
   `KeyboardAccessibilityLayer` (one invisible, pointer-input-free box per key and strip button, laid out from
   the same `KeyGeometry`) only when `AccessibilityManager.isEnabled`. Without a service nothing extra is
   composed, so the touch path and its latency are untouched. The boxes carry no pointer input, so touches
   still reach the canvas; with a service on, touch exploration turns finger movement into hover events that
   Compose resolves to the boxes, and a double tap (or "activate on lift") performs the box's click. Switch
   Access and Voice Access get the same nodes. While the layer is shown the canvas itself is hidden from the
   accessibility tree (and while a panel covers the keys), so nothing is announced twice.
2. **The tree is a pure model.** `KeyAccessibility` turns geometry, `KeyboardState` (shift, page, enter
   action), the strip content and a bag of already-translated labels into `A11yNode`s with a description, an
   optional state ("caps lock on"), a target and extra actions. Long-press alternatives become custom actions
   ("Type é"); the globe key offers "Choose keyboard" (its long press). Nodes come in reading order: strip
   left to right, then rows top to bottom. `AccessibilityTargets` maps targets to the same controller calls the
   touch path makes.
3. **Panels and settings** get roles, selected/state semantics (never colour alone), separate nodes for
   nested buttons, a click action on the hold-to-repeat backspace, headings, live regions for dictation state
   and the contrast warning, and merged label+control semantics with units for sliders and switches. Touch
   targets in the app screens are at least 48 dp (`MinTouchTarget`); the keyboard's own panels keep their
   compact sizes because they live inside the keyboard height.
4. **Translations are checked by Gradle.** `translationCheck` (root `check`) compares every module's
   `values/strings*.xml` with `values-es/strings*.xml`: same names, same printf placeholders, nothing extra,
   and no other language folder without a checker. A new language must be added to
   `TranslationScanner.scan`'s list on purpose.
5. **Licenses data is a hand-written JSON** (`app/src/main/assets/licenses/components.json`) plus bundled
   license texts, not a generated file: `THIRD_PARTY.md` tables mix prose and several formats, and the
   attribution wording (copyright lines, links, which text applies) is curated. `LicenseCatalogTest` keeps it
   in sync: each row of the component and font tables of `docs/THIRD_PARTY.md` must be named in some
   component's `thirdPartyRows`, no stale row may remain, every shipped library of
   `gradle/libs.versions.toml` must be covered by `modules`, every license id must have a bundled text that is
   really that license, and links must be https. Adding a row to `THIRD_PARTY.md` therefore fails the build
   until the data file gets its entry. The font texts are read from `:ime`'s assets (merged into the APK), so
   they exist once.
6. **Lint and detekt** already fail on warnings (`lint.warningsAsErrors`, detekt in `check`); 8.6 keeps it so
   and adds no suppression without a comment saying why.
