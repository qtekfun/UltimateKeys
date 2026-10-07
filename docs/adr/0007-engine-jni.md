<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0007: AOSP engine via JNI

Status: accepted

## Context

`SPEC.md` section 2 reuses the AOSP LatinIME native engine for dictionary lookup, autocorrect,
next-word prediction and history learning. Phase 2 (tasks 2.1 to 2.3) must vendor it, build it with
CMake and expose it to Kotlin.

## Decision

**Source and pin.** The C++ in `native/jni` is vendored into `third_party/aosp-latinime/` from
`android.googlesource.com/platform/packages/inputmethods/LatinIME`, commit
`127336e9f29d69607eab55982324b210279ae8c5` (branch `main`, 2025-02-26), recorded in
`UPSTREAM_COMMIT`. Every file carries the Apache-2.0 header, so the license is compatible with
GPL-3.0-or-later and F-Droid. `LICENSE` and `NOTICE` come from upstream's `NOTICE` (which is the
license text). The GitHub aosp-mirror repository no longer exists; googlesource is the source.
Size: about 1.7 MB of source, 220 files.

**What was stripped.** Only what cannot work or is not needed: `Android.bp`, the host C++ tests,
`dicttoolkit`, and the bulk personalization import `updateEntriesForInputEventsNative` (it reads
fields of an original-app Java class). No Java/Kotlin was copied. Details and rationale per file are
in `third_party/aosp-latinime/MODIFICATIONS.md`.

**JNI registration.** Upstream registers natives on four Java classes. We changed the four
class-name literals to one constant and declared all 30 remaining natives as `external fun` on the
Kotlin object `com.qtekfun.ultimatekeys.engine.NativeEngine`. `RegisterNatives` requires every table
entry to exist, hence the complete declaration list. Signatures were cross-checked mechanically
against `javap` output. `consumer-rules.pro` keeps the class and its native methods under R8.

**Build.** `engine/src/main/cpp/CMakeLists.txt` builds `libukengine.so` from a sorted glob of the
vendored sources (C++17, NDK 28.2, ABIs from `uk.android.native`). For reproducibility:
`-ffile-prefix-map` for the repository, build directory and NDK, `-fno-ident`, and
`-Wl,--build-id=none`; 16 KB page alignment is requested explicitly. Upstream's strict warning flags
are not applied to vendored code. Whether two clean builds produce identical bytes is verified by the
Phase 2.8b CI job, not here.

**Kotlin layering.**

- `NativeEngine` is the raw JNI surface (internal, no logic).
- `NativeBridge` is a small Kotlin-typed interface; `JniNativeBridge` implements it with the array
  plumbing and is the only caller of `NativeEngine`. Tests use `FakeNativeBridge`.
- `AospSuggestionEngine` implements the existing `SuggestionEngine` unchanged. Per language it
  queries three dictionaries: the read-only main dictionary (located through the public
  `DictionaryLocator`, file path plus offset/length so it can be mapped straight out of an APK
  asset), an updatable history dictionary (format 4 with historical info; learns words and
  n-grams, forgets with age) and an updatable user dictionary. Results are merged by word keeping the
  best score, sorted, and re-capitalized to match what was typed. Autocorrect is set only for the top
  candidate when the typed word is unknown, the engine flags it appropriate and its normalized score
  reaches a threshold (default 0.185, the original "modest" setting).
- `KeyboardGeometry` feeds the engine's typo-proximity grid. `:layouts` does not exist in this
  form yet, so a QWERTY geometry (plus `n-tilde` for Spanish) is generated; a caller can pass its
  own through `geometryFor`.
- Private mode: the constructor takes `learningEnabled: () -> Boolean`, checked before each `learn`;
  `:privacy` wires it in Phase 4.

**Threading.** Native dictionaries and traverse sessions are not thread-safe and every call can take
milliseconds (mmap, trie walk, disk flush). The engine must be called from one background dispatcher,
never from the main thread. As a safety net all public methods hold one lock; a concurrent call waits
rather than corrupting native state. Learned data is flushed to disk every 10 learned words, after
user-dictionary edits, and on `close()`. After `close()` every call is a harmless no-op.

**Storage.** `storageDir/history-<lang>/` and `storageDir/user-<lang>/` are format-4 dictionary
directories created on first use; a directory that fails to open is deleted and recreated. `clearLearned`
closes the handles, deletes these directories and lets them be recreated lazily.

## Consequences

- The reused code is isolated, documented and replaceable; updating the pin is a documented procedure.
- Anything native is only testable on a device. JVM tests cover all Kotlin decisions through the fake;
  an instrumented test with a real dictionary is still required (Phase 2.8).
- The typing-proximity quality depends on the generated geometry; it should be replaced by the real
  layout geometry when `:layouts` can supply it.
- The engine ignores touch coordinates (typed input has none); gesture typing is out of scope.
