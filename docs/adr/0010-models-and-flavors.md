<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0010: Dictation models and the two flavors

Status: accepted

## Context

`SPEC.md` sections 7 and 8 ask for two flavors: `full` (a `base` model bundled, no INTERNET permission at all) and `lite`
(nothing bundled, INTERNET used only by a model downloader). Models are never committed, are verified by SHA-256, and can
also be imported from a file. Phase 6 left a `ModelSource` seam and a stand-in that picks the first `.bin`.

## Decision

**One catalog, three readers.** `voice-models/src/main/assets/models.json` pins each model (id, file, size, SHA-256, URL to a
commit of the official Hugging Face repository, licence). The app parses it at runtime (`ModelCatalog`, strict validation:
https only, safe file names, 64 hex digits), and the `full` build parses the same file in Gradle. No second copy exists.
Models: `base` and `small`, multilingual, `q5_1` (details and how they were hashed in `docs/MODELS.md`). `tiny` stays a CI test
fixture only.

**Where code lives.** All logic is in `:voice-models`, pure Kotlin and unit tested on the JVM: catalog, `ModelStore` (staging,
hashing, atomic moves), `ModelImporter`, the `ModelDownloader` state machine, `BundledModelInstaller`, `SelectedModelSource`,
and `ModelsController` (what the manager screen shows and does). The downloader talks to an `HttpSource` interface, so the
module contains no network code. The two flavors differ only in `app/src/full` and `app/src/lite`, which both define the same
top-level functions (`createProvisioner`, `installBundledModels`) behind the `ModelProvisioner` interface:

- `full` provisions by copying the bundled asset (also used to restore a deleted model); it has no network class, no
  WorkManager and no permission.
- `lite` provisions with WorkManager and `HttpUrlConnectionSource`, and its own manifest adds INTERNET. The WorkManager
  dependency is `liteImplementation`, so it is not in the `full` APK at all.

Alternatives rejected: a Gradle flavor on `:voice-models` (more variant plumbing for no gain, since `:app` already owns the
flavors); a network library (CLAUDE.md forbids it; `HttpURLConnection` is enough for one ranged GET); gating the downloader at
runtime in one code base (a permission and code that `full` should not contain).

**Download.** One unique WorkManager job per model; constraints `UNMETERED` (Wi-Fi only, default on) or `CONNECTED`, plus
"storage not low". Resumable by design: the partial file survives cancellation of the coroutine, a stopped worker, process death
and reboot, and the next run asks `Range: bytes=<size>-`. A 206 whose `Content-Range` does not start at the offset, a 416 or a
server that ignores the range restart from zero (one restart allowed). A wrong checksum deletes the file and is final. 5xx, 408,
429 and I/O errors are retried with exponential backoff, at most five times, then reported. Redirects are followed by our code
(https only, same range request on every hop), because the platform would not preserve those guarantees. The worker is not a
foreground service: the data is resumable, so a system stop costs only time, and a foreground notification would need another
permission. The user cancelling discards the partial file.

**Verification.** Every route into `filesDir/models` (bundled copy, download, import) hashes while or after writing and moves
the file in atomically from `models/.staging`. "Installed" means a file with the pinned name and size exists, because only a
verified file can have arrived under that name. The SHA-256 is not recomputed at every start (60 to 190 MB would cost seconds on
each keyboard launch).

**Bundled model in `full`.** `fetchBundledModel` mirrors `FetchDictionariesTask`: reads `models.json`, downloads once into
`$GRADLE_USER_HOME/ultimatekeys-models/<sha256>.bin`, verifies, copies as `assets/models/base.ggml` (a generated source
directory only for `full` variants). `noCompress` for the `ggml` extension keeps the entry stored: the bytes equal the pinned
file (reproducible-build job), nothing is inflated at install time, and the extension is distinct from the dictionaries' `.bin`
so those stay compressed. First run (`UltimateKeysApp`, background thread) copies it to `filesDir/models` with a checksum
check and writes a marker so a deleted model is not resurrected. Android's asset manager cannot hand out a file path or an
mmap of an APK entry through the public API we use, and whisper.cpp wants a path, so one copy to app storage is needed; that
copy is the "first-run install". Cost: the model occupies APK size plus an equal amount of app storage.

**Selection and settings.** `KeyboardSettings` gains `dictationLanguage` (`auto`, `es`, `en`), `dictationSilenceMs` (500 to
5000, default 1500), `dictationModelId` and `modelDownloadWifiOnly`, stored in the existing DataStore. The keyboard service
reads them through `controller.settings` in the `config` lambda of `DictationController` and in a `SelectedModelSource`; the
chosen model if installed, otherwise the first installed one, so deleting the active model never leaves dictation without one
when another is present. The voice panel's "Choose a model" starts `ModelsActivity` by class name (the `:ime` module cannot see
`:app`). The activity holds the model manager and the dictation settings screens.

**Unknown imported files.** Magic `lmgg` plus a vocabulary size in the Whisper range pass the sanity check; a file whose hash
matches the catalog is installed as that model; any other is installed only after an explicit warning that shows size and
checksum. Limits: 1 MB to 4 GB.

**Checks.** `verifyFullHasNoInternet` (existing, now with comment-aware matching) and the new `verifyLiteHasInternet` run in
`check`; their rules are the unit-tested `ManifestRules` in `build-logic`.

## Consequences

- The `full` APK is about 60 MB larger than `lite` and needs a similar amount of free storage after first run; the build
  needs network access the first time (cached afterwards, cached in CI by the Gradle cache).
- `lite` cannot dictate until a download or import completes; the voice panel says so and opens the manager.
- Real-network behaviour (CDN redirects, Wi-Fi constraint, process death during a download) is covered by unit tests with fakes
  and a local HTTP server, and by device checks in `docs/HUMAN_VERIFICATION.md`.
- `docs/DISTRIBUTION.md` records the F-Droid assessment for the bundled model.
