<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Performance

Targets (SPEC §12): key press → character committed < 16 ms p95 on the Find X8 Pro; suggestions updated < 30 ms p95; first show after IME start < 300 ms.

## How it is measured

- `LatencyTracker` records, for every key release that commits text, the time between the start of the release handler and the end of the editor call (`System.nanoTime`). It keeps the last 512 samples locally and nothing is sent anywhere.
- In debuggable builds the service logs `key-to-commit p50/p95` to logcat (tag `UKLatency`) every 20 samples.
- This covers our own processing, not touch delivery or the target app's time to apply the edit.

## First numbers

See the table below; each row states the device and build.

| Date | Device | Build | p50 | p95 | Notes |
|---|---|---|---|---|---|
| 2026-10-07 | Pixel 8 (Android 17) | full debug | 1.5 ms | 1.9 ms | 60 taps via adb; our own processing only |
