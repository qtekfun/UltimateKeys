# Modifications to the vendored AOSP LatinIME native engine

This directory contains the C++ code of the Android Open Source Project input method engine
(`packages/inputmethods/LatinIME/native/jni`), licensed under the Apache License 2.0 (see
`LICENSE` and `NOTICE`). As required by section 4(b) of the license, this file states every change
made to the upstream files.

- Upstream: <https://android.googlesource.com/platform/packages/inputmethods/LatinIME>
- Pinned commit: see `UPSTREAM_COMMIT` (`127336e9f29d69607eab55982324b210279ae8c5`, 2025-02-26,
  branch `main`).
- Only the native C++ is used. No Java or Kotlin from upstream is included; the Kotlin side
  (`engine/src/main/kotlin/...`) is written from scratch.
- Files under `native/jni/` keep their original Apache-2.0 license headers. Our own build glue
  (`engine/src/main/cpp/CMakeLists.txt`) lives outside this directory.

## Files not copied

| Upstream path | Why |
| --- | --- |
| `native/jni/Android.bp` | Soong build file. Replaced by `engine/src/main/cpp/CMakeLists.txt`. |
| `native/jni/run-tests.sh`, `native/jni/tests/` | Host-side C++ unit tests that need the AOSP build system. Not run in our CI. |
| `native/dicttoolkit/` | Host command line tool to build dictionaries. Not needed at runtime. |
| Everything outside `native/` | Java/Kotlin code and resources. Out of scope (see `SPEC.md` section 2). |

## Files modified

All edits are marked with the text "UltimateKeys" or are the replacement of a class-name literal.

| File | Change | Why |
| --- | --- | --- |
| `native/jni/jni_common.h` | Added `kNativeEngineClassName` (`com/qtekfun/ultimatekeys/engine/NativeEngine`). | Upstream registers natives on four different Java classes of the original keyboard. We register all of them on one Kotlin class. |
| `native/jni/com_android_inputmethod_latin_BinaryDictionary.cpp` | Registration class path now `kNativeEngineClassName`. Removed `latinime_BinaryDictionary_updateEntriesForInputEvents` and its `updateEntriesForInputEventsNative` table entry. | Class adaptation (see above). The removed function reads fields of the Java class `WordInputEventForPersonalization` of the original app, which we do not have; it only serves a bulk personalization import that we do not use. Single-word learning (`updateEntriesForWordWithNgramContextNative`) is kept. |
| `native/jni/com_android_inputmethod_keyboard_ProximityInfo.cpp` | Registration class path now `kNativeEngineClassName`. | Class adaptation. |
| `native/jni/com_android_inputmethod_latin_DicTraverseSession.cpp` | Registration class path now `kNativeEngineClassName`. | Class adaptation. |
| `native/jni/com_android_inputmethod_latin_BinaryDictionaryUtils.cpp` | Registration class path now `kNativeEngineClassName`. | Class adaptation. |
| `native/jni/src/dictionary/structure/v4/ver4_dict_constants.cpp` | `MAX_DICTIONARY_SIZE` 8 MB to 32 MB, `MAX_DICT_EXTENDED_REGION_SIZE` 1 MB to 8 MB. | The limits were sized for small user-history dictionaries. Building a main dictionary of ~160,000 words fails at ~39,700 words with them (ADR 0008). |
| `native/jni/src/dictionary/utils/buffer_with_extendable_buffer.cpp` | `DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE` 1 MB to 8 MB. | Same reason. |

Apart from the two files above, the files under `native/jni/src/` are byte-identical to the pinned upstream commit.

`jni_common.cpp` (the `JNI_OnLoad` entry point) is unchanged: it calls the four `register_*`
functions above, which now all target `NativeEngine`. `RegisterNatives` fails if a table entry has
no matching `external fun`, so `NativeEngine.kt` declares every remaining native method with the exact
signature of the table (cross-checked in the build of this change).

## Build differences

The upstream `Android.bp` flags `-Werror -Wall -Wextra -Weffc++ ...` are not used; our CMake build
passes `-Wno-unused-parameter -Wno-unused-function -Wno-deprecated-declarations`, deterministic
path-prefix mapping and `-Wl,--build-id=none`, and compiles with C++17 (NDK clang). The x86
`-mstackrealign` hack from upstream is kept for `x86` ABIs. See `docs/adr/0007-engine-jni.md`.

## Updating the pin

1. Fetch the new upstream commit, copy `native/jni` as above, drop the files in the table.
2. Re-apply the modifications in this file; write the new hash to `UPSTREAM_COMMIT`.
3. Run `:engine:assembleDebug` and the on-device engine tests.
