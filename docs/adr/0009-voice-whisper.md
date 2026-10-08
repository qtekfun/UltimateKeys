<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0009: Offline dictation on whisper.cpp

Status: accepted

## Context

`SPEC.md` section 7 asks for offline dictation with Whisper models, a voice panel, automatic stop on silence,
Spanish and English, no audio on disk and no learning while private. Phase 6 builds the engine side and the panel;
model download, model selection and the settings screens are Phase 7.

## Decision

**Source and build.** whisper.cpp is a git submodule at `third_party/whisper.cpp`, pinned to the release tag `v1.9.5`
(details in `docs/THIRD_PARTY.md`). `voice/src/main/cpp/CMakeLists.txt` adds it with `add_subdirectory` as static
libraries, CPU backend only (`GGML_NATIVE`, OpenMP, dynamic backends and GPU backends off), and links them with our JNI
file into `libukwhisper.so`. Flags are the same on every ABI and independent of the Gradle build type: `-O3`, NEON on
arm64 (part of the baseline) and armeabi-v7a (`-mfpu=neon-vfpv4`). Reproducibility follows `:engine`:
`-ffile-prefix-map` for repository, build directory and NDK, `-fno-ident`, `-Wl,--build-id=none`, 16 KB page alignment.
The arm64 baseline is plain `armv8-a`: dot-product and fp16 instructions would make the library crash on older cores.
Runtime selection of a faster variant (as whisper.cpp's own Android example does with a second library) is a Phase 8
performance task, together with `audio_ctx` trimming for short phrases.

**JNI.** One Kotlin object, `NativeWhisper`, with static-name JNI functions (`load`, `free`, `abort`, `transcribe`,
`pinCurrentThread`, `systemInfo`). It is wrapped by `WhisperBackend` so `WhisperTranscriber` can be tested on the JVM with a
fake. A missing library surfaces as `ModelLoadException`, never as a crash of the keyboard.

**Language.** `DictationLanguage.AUTO` passes both candidates to the native side, which computes the language
probabilities for the audio and picks the more probable of `es` and `en`, then decodes with that language forced. This
runs the encoder twice (detection, then decoding); the cost is accepted for Phase 6 and noted for Phase 8. `SPANISH` and
`ENGLISH` force the language and skip detection. In-sentence language switching is not supported by small models.

**Threads.** `CpuTopology` reads each core's maximum frequency from sysfs, calls the cores above 70 % of the fastest "big"
and uses at most four threads (more does not help, whisper is memory bound). The dedicated inference thread pins itself to
the big cores before the first decode, so the workers whisper creates inherit that mask. Unreadable sysfs falls back to
half the processors, unpinned. Cancellation sets an abort flag that whisper polls.

**Audio.** `MicrophoneSource` records `VOICE_RECOGNITION` at 16 kHz mono as float (16-bit as a fallback) in 30 ms reads.
`AudioRecorder` keeps samples in a growing in-memory array (cap: 60 s), drops the trailing silence beyond a 400 ms tail
and wipes the array when done. Nothing is written to disk. `EnergyVad` measures a noise floor in the first 300 ms,
follows it slowly and calls a frame speech when it is 10 dB above the floor and above -48 dBFS; it needs 150 ms of
speech in total (clicks are not speech), then ends the recording after `silenceTimeoutMs` (default 1.5 s, 0.5 to 5 s) of
silence, or abandons it after 8 s without speech. Whisper's outputs for non-speech (`[BLANK_AUDIO]`, `(music)`, notes)
are removed by `TranscriptCleaner`; an empty result is the "nothing was heard" state.

**State machine.** `DictationController` (in `:voice`, pure Kotlin) owns one dictation at a time: permission check,
model check, recording while the model loads in parallel, transcription, result. Its states (`Idle`, `Listening`,
`Transcribing`, `Failed(error)`) are what the panel draws. The configuration (language, VAD timings) is read through a
lambda, so Phase 7's settings plug in without changes. `ModelSource` is the seam for the model manager; until then
`DirectoryModelSource` returns the first `*.bin` in `filesDir/models`.

**Permission.** `RecordAudioPermissionActivity` is a transparent, non-exported activity that requests `RECORD_AUDIO` and
finishes; the answer is published on `MicrophonePermissionFlow.results` (same process). The panel's error state carries
the explanation text; after a refusal in this session the button becomes "Open settings". A granted answer while the
"no permission" state is showing starts the dictation by itself.

**Keyboard integration (`:ime`).** `DictationHost` connects the controller to the keyboard (buttons, permission answer,
error shortcuts). `KeyboardController` exposes `features.voice` when a host is provided, opens it on `KeyAction.MIC`
(bottom row or suggestion bar, as the style's `micPlacement` says) and inserts results through
`InputLogic.insertDictation`. The panel replaces the key area (same height, cross-faded with the style's motion
settings; reduced motion when the style has none), and touches on the keys are cancelled while it is up. Hiding the keyboard
cancels a dictation, so the microphone is never kept in the background.

**Insertion.** `DictationFormatter` (pure) adds a leading space unless the cursor follows whitespace or an opener, a
trailing space when a word follows, capitalizes at the start of a sentence and lowercases a leading function word
(a short Spanish and English list) when the phrase continues a sentence; names, acronyms and "I" are left as heard.
Fields that are not prose (email, URL, number) get the text exactly as heard. The text is one `commitText` inside one
batch edit, which editors record as a single undo step. Words are passed to learning only when private mode is off (the
engine wrapper also refuses while private).

**Tests.** JVM tests cover the VAD, recorder, controller, transcriber (with a fake backend), formatter, insertion,
host and strip logic; Roborazzi goldens cover each panel state. `WhisperJniTest` (instrumented, `:app`) runs the real
library with the `tiny` model on the CI emulator: transcription, forced language and the whole dictation flow with a
recorded sample as the audio source. The model comes from a CI cache keyed by its pinned SHA-256 and is copied into the
app's private files with `run-as`; the test skips when the files are absent (local runs) and fails in CI
(`requireWhisper=true`).

## Consequences

- No model or audio is ever in the repository; the production models are Phase 7's concern.
- The first dictation after a cold start pays the model load (hidden by recording in parallel); unloading after idle is
  Phase 8 (8.2).
- The panel's strings live in `:ime` (English default, Spanish translation); `:voice` has no strings because its activity
  has no UI.
- Accuracy of the energy VAD in loud places is a judgment call, listed in `docs/HUMAN_VERIFICATION.md`.
