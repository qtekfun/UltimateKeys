<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0023: Per-screen settings on foldable phones

Status: accepted

## Context

A foldable has two very different screens: a phone-sized cover screen and a tablet-sized inner one. One shared
height, margin, width and split configuration cannot suit both, upright or sideways. Non-foldable devices must
keep behaving exactly as before.

## Decisions

1. **Detection uses the platform only.** The device folds if it declares the `android.hardware.sensor.hinge_angle`
   feature; the screen in use is read from `Configuration.smallestScreenWidthDp` (>= 600 dp is the inner screen,
   below it the cover screen). No dependency is added: `androidx.window` would be FOSS, but its fold features only
   exist while a window spans the fold, and it adds nothing to which of the two screens is in use. The decision
   is the pure function `ScreenProfiles.decide`, tested without a device.
2. **Three profiles**: `NORMAL` (not a foldable), `FOLDABLE_INNER`, `FOLDABLE_OUTER`. Portrait and landscape are
   not part of the profile: the settings already have separate upright and sideways values.
3. **Storage.** `NORMAL` keeps the original DataStore keys. Each foldable screen has its own keys with a prefix
   (`fold_inner_`, `fold_outer_`) for the eight size-related settings (upright height, bottom margin, side margin,
   sideways height and width, sideways microphone, split mode and split gap). A key never written falls back to the
   original value, and only values the user changed are written, so nothing is migrated and nothing is lost; the
   rest of the settings are shared.
4. **API.** `SettingsRepository.settings`/`update` keep meaning "the screen in use", so the keyboard needs no
   change; `settingsOf(profile)`/`updateOf(profile, ...)` let the settings edit the other screen. The process-wide
   `ScreenProfileTracker` is refreshed by the keyboard service and the settings screens on configuration changes.
5. **UI.** The typing settings show a "Screen" selector only on a foldable.

## Consequences

- A foldable that does not declare the hinge angle sensor is treated as a normal phone (everything keeps working,
  with one shared configuration). A flip phone's main screen is below 600 dp, so it is the "cover" one.
- Not verified on hardware yet: see `docs/HUMAN_VERIFICATION.md`.
