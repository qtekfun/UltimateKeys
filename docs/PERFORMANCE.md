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

## Release APK size (Phase 7)

Unsigned release APKs (R8 and resource shrinking on), built on 2026-10-08 from the Phase 7 branch:

| Flavor | Size | Notes |
|---|---|---|
| `lite` | 15 876 718 bytes (15.1 MiB) | No model; the person downloads or imports one. |
| `full` | 75 400 903 bytes (71.9 MiB) | `lite` plus the `base` model (59 707 625 bytes), stored uncompressed because it does not compress. |

After first run the `full` app also holds a copy of the model in its private storage (another 57 MiB); the `lite` app holds
the model only if one was downloaded or imported (57 MiB for `base`, 181 MiB for `small`). See `docs/DISTRIBUTION.md` for what
this means for F-Droid.

## Where each target stands (Phase 8, task 8.1)

Honest summary: the machinery to measure every target exists and the parts that can be checked without a phone pass. The numbers that need
real hardware (the Find X8 Pro or a Pixel) are listed in `docs/HUMAN_VERIFICATION.md` and are not claimed here.

| Target (SPEC section 12) | How it is checked | Status |
|---|---|---|
| Key press to character committed, p95 < 16 ms | `LatencyTracker` around the release handler; debug builds log `key-to-commit p50/p95` (tag `UKLatency`) | Our own processing measured at 1.5 ms p50 and 1.9 ms p95 on a Pixel 8 (first numbers above). Touch delivery and the target app are outside our control. Re-measure on the Find X8 Pro: human check. |
| Suggestions updated, p95 < 30 ms after each keystroke | `EngineJniTest.suggestionLatencyStaysUnderTarget` runs on the CI emulator with the real dictionaries and fails when p95 reaches 30 ms; suggestions run off the main thread on one background dispatcher | Enforced in CI on every PR (x86 emulator). Real-device number: human check. |
| Keyboard first show after IME start < 300 ms | `FirstShowTimer`: debug builds log `first-show N ms` (tag `UKLatency`) from service creation to the first drawn frame of the keys | Instrumented in this phase; the number must be read on a device (human check). Heavy work is kept off this path: the dictionaries are built in the background after the first frame, and the speech model is never loaded until dictation is used. |
| Gesture candidates < 50 ms p95 after finger lift | `GestureLatencyTest` (JVM) over the full vocabulary | 3.6 ms p95 on a quiet desktop, 8.2 ms under load (see `docs/gesture/RESULTS.md`). Device number: human check. |

### Memory held by dictation (task 8.2)

- The speech model is loaded when listening starts (it loads while the person is already speaking) or earlier, while the permission prompt is
  shown (`DictationController.warmUp`).
- It is freed two minutes after the last dictation (`DEFAULT_IDLE_UNLOAD_MS`), when the system reports memory pressure at the background
  level or worse (`onTrimMemory`, only while no dictation is running), and when the keyboard service is destroyed.
- Recordings are held in memory only, capped at 60 seconds, and wiped as soon as they are transcribed.
- The `base` model is 57 MiB on disk; it is mapped when loaded and released on unload.

### Release APK sizes (v0.9.1, unsigned size of the signed artifacts on GitHub Releases)

| Flavor | Size | Notes |
|---|---|---|
| `lite` | 16 000 299 bytes (15.3 MiB) | No model. |
| `full` | 75 524 484 bytes (72.0 MiB) | `lite` plus the `base` model, stored uncompressed. |

## Many languages (ADR 0022)

### Release APK sizes with 24 dictionaries (this branch, unsigned release builds, R8 on)

| Flavor | Size | Change against v0.9.1 above |
|---|---|---|
| `lite` | 41 401 372 bytes (39.5 MiB) | +25 401 073 bytes (+24.2 MiB) |
| `full` | 100 925 557 bytes (96.3 MiB) | +25 401 073 bytes (+24.2 MiB) |

The whole increase is the 22 added word lists (they are gzip data, so they do not compress further). The rule set by the owner was to bundle every
language in both flavors when all lists together add less than 25 MiB, and to make the rest importable or downloadable otherwise: 24.2 MiB is under the
limit, so everything is bundled and there is no import or download path. The margin is small (0.8 MiB): a larger list or a new language will need the
other route (the installer and `BinaryDictionaries` already work per language, so the download of one list is the only missing piece).

### On the device, per enabled language

- Installed word list: about 1 MiB (the bundled file, copied on first use of the language; none for languages that are off).
- Built binary dictionary: a few MiB (English measured at 4.6 MiB for 160,000 words; the lists are capped at the 250,000 most frequent words, so
  at most about 7 MiB). Built once, in the background, in a few seconds per language; removed when the language is switched off.
- Gesture vocabulary: about 40 bytes a word, at most 120,000 words per language (about 5 MiB each); all enabled languages together stay near 30 MB at
  the limit of six.
- Native suggestion engine: three dictionaries per language are memory-mapped (main, history, user); a language that is switched off is closed.

### Latency with six languages

- Gestures (JVM, `ManyLanguagesGestureTest`): six languages (French, Spanish, English, German, Russian, Greek, 698,000 words) decode in p50 0.9 ms and
  p95 4.1 ms, the same as two languages (p95 4.2 ms), because the keys of the first and last point prune the candidates.
- Suggestions: each enabled language adds three native queries to every keystroke, so six languages cost about three times what two do. The engine is
  serialized on one background thread. The real-device number with six languages is a human check (`docs/HUMAN_VERIFICATION.md`); the CI emulator
  test measures the Spanish and English case.
