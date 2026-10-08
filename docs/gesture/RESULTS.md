<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Gesture typing: results

Plan tasks 10.6 (accuracy) and 10.7 (latency). Everything here comes from the offline harness on the JVM:
**synthetic gestures, not people.** Read the limits at the end before trusting a number.

## Method

* Words: the first 5,000 words of each pinned word list (`dictionaries/sources.properties`, most frequent
  first; offensive and not-a-word entries removed). Words that cannot be traced on the layout (single
  letters, a letter without a key, digits) are skipped: 7 of 5,000 for Spanish, 3 for English.
* Keyboards: the real `es_qwerty` and `en_qwerty` layouts through `SurfaceSpec.geometry`
  (1080 px wide, density 2.75, default style).
* Decoder: the shipped one (`GestureConfig()` defaults) over the full shipped vocabulary: both languages
  together (255,127 words, frequency 30 or more), Spanish primary. This is the hard case: a Spanish word
  competes with about 100,000 English ones.
* Gestures: `SyntheticGestureGenerator`, seeded. Each key of the word is missed by a Gaussian amount
  (sigma per axis), the whole path is shifted by a hand offset, corners are rounded by Chaikin corner cutting,
  and the curve is sampled every 0.08 to 0.25 key with a little jitter.

| Profile | Key miss sigma | Hand offset sigma | Corner cut |
|---|---|---|---|
| careful | 0.10 key | 0.04 key | 0.08 |
| typical | 0.20 key | 0.08 key | 0.15 |
| sloppy | 0.35 key | 0.15 key | 0.25 |

Reproduce: `./gradlew :ime:testDebugUnitTest --tests '*GestureAccuracyTest*' -Pgesture.full=true`, which
writes `ime/build/gesture-results.md`. The normal test run checks a sample (every tenth word, typical and
careful profiles) against the target.

## Accuracy: 5,000 most common words, no context

| Language | Profile | Words | Top-1 | Top-3 | Top-5 |
|---|---|---|---|---|---|
| es | careful | 4993 | 96.2% | 99.8% | 100.0% |
| es | typical | 4993 | 87.2% | **97.2%** | 98.5% |
| es | sloppy | 4993 | 44.8% | 61.9% | 68.1% |
| en | careful | 4997 | 92.9% | 99.8% | 99.9% |
| en | typical | 4997 | 82.7% | **96.8%** | 98.2% |
| en | sloppy | 4997 | 46.4% | 64.2% | 70.5% |

**Target (top-3 of at least 90% on the 5,000 most common words per language): met for the careful and
typical profiles, not for the sloppy one** (62% and 64%).

## Language context and mixing (typical profile)

| Language | Scenario | Words | Top-1 | Top-3 | Top-5 |
|---|---|---|---|---|---|
| es | mixed vocabulary, [también, estaba] before | 4993 | 87.5% | 97.5% | 98.6% |
| es | own language only | 4993 | 87.7% | 97.6% | 98.8% |
| en | mixed vocabulary, [the, would] before | 4997 | 88.5% | 97.7% | 99.0% |
| en | own language only | 4997 | 90.1% | 98.2% | 99.2% |

Carrying both languages costs 0.4 points of top-3 for Spanish and 1.4 for English with no context (first
table against the own-language rows), because English words start with the 0.75/0.25 prior against them.
Two words of English context recover that (96.8% to 97.7%). Spanish, the primary language, hardly notices.

## By word length (typical profile, no context)

| Language | Letters | Words | Top-1 | Top-3 |
|---|---|---|---|---|
| es | 2-3 | 128 | 79.7% | 98.4% |
| es | 4-5 | 936 | 80.0% | 96.4% |
| es | 6+ | 3929 | 89.1% | 97.4% |
| en | 2-3 | 236 | 77.5% | 97.0% |
| en | 4-5 | 1336 | 78.6% | 96.3% |
| en | 6+ | 3425 | 84.7% | 97.0% |

## Latency (task 10.7)

`GestureLatencyTest`: 2,000 typical gestures (every twentieth word of the 20,000 most common of each
language), the full 255,127-word vocabulary, with a language context, after 300 warm-up decodes. CPU time of
the decoding thread, JVM 21 on a desktop x86-64 (development machine, other builds running):

| Run | p50 | p95 | p99 | max | wall-clock p95 |
|---|---|---|---|---|---|
| quiet machine | 0.69 ms | **3.6 ms** | 4.9 ms | 6.8 ms | 3.6 ms |
| other builds running | 1.8 ms | **8.2 ms** | 11.5 ms | 16.2 ms | 8.3 ms |

The test asserts p95 below 50 ms (the requirement). Per gesture about 4,500 words pass the end-key pruning
and 3,300 are scored.

A phone has not been measured (no device work in this phase). For the budget: even at ten times the quiet-machine
time the p95 stays near 36 ms, and decoding runs on the suggestion dispatcher, never on the main thread; the
debug build logs `gesture lift-to-candidates` under the `UKLatency` tag so the real number can be read from
logcat (human check 10.8). If a device is too slow, the levers are `GestureSupport.MIN_FREQUENCY` (a smaller
vocabulary), `GestureConfig.resamplePoints` and `endpointRadius`.

The first version of the scorer took p95 of 12 to 70 ms depending on machine load; it spent most of its time
in `Math.hypot`. Squared-distance square roots, frequency-ordered buckets and early abandon made it about six
times faster with identical results.

## What limits accuracy

* **Noise larger than the keys.** At the sloppy profile each letter is missed by 0.35 key per axis and
  corners are cut by a quarter: a neighbouring key's word is as likely as the intended one. No template
  matcher can recover that; only a better language model (context) helps, and this one is a unigram with a
  next-word bonus.
* **Words that differ in one adjacent key**: `llamado/llenado`, `costas/costa`, `columna/colina`. Top-3
  catches most of them; top-1 does not.
* **Accents and inflections are invisible to the path**: `esta/está/ésta`, `hable/hablé`. Frequency decides;
  the other forms are in the strip.
* **Short words** (2 to 3 letters) have the weakest top-1 (about 78%): a short path has little shape.
* **Coarse frequencies**: the pinned lists have only about 200 distinct frequency values for 250,000 words
  (for instance several hundred words share 117 around rank 5,000), so the unigram cannot order rare words
  that compete with each other.

## Limits of this evaluation

* The generator is a model of a hand, not data from hands. Its noise (independent Gaussian misses, rounded
  corners, steady speed) is probably kinder than real use in some ways (no hesitations, no swerving, no
  thumb-reach bias) and harsher in others (every key is aimed with the same care). The decoder was tuned on
  the same family of noise; the reported runs use a different seed and all 5,000 words, but not different
  physics. Absolute numbers will move on real gestures; the ranking of settings should hold.
* Words are glided in isolation, so the `[también, estaba]` style context is a stand-in for a sentence.
* The profiles and the 90% target say nothing about how gesture typing *feels* (trail, activation distance,
  alternatives in the strip). That is task 10.8, a human check on the Find X8 Pro.
