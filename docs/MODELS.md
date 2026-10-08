<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Dictation models

Dictation runs Whisper models in the ggml format of whisper.cpp (see `docs/adr/0009-voice-whisper.md`). Models are data, not
code, and are **never committed**. This page lists the models the app supports, where each one comes from and how it is
verified. The machine-readable source of truth is `voice-models/src/main/assets/models.json`: the app reads it at runtime
(the `lite` downloader, the model manager and the import check) and the `full` build reads it to fetch the bundled model, so
the pins below cannot drift apart.

## Supported models

Both are the multilingual (not `.en`) models, quantized to `q5_1`, from the official `ggerganov/whisper.cpp` repository on
Hugging Face, pinned to commit `5359861c739e955e79d9a303bcbc70fb988958b1`.

| Id | File | Size (bytes) | SHA-256 | Licence |
|---|---|---|---|---|
| `base` | `ggml-base-q5_1.bin` | 59 707 625 (about 57 MiB) | `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` | MIT |
| `small` | `ggml-small-q5_1.bin` | 190 085 487 (about 181 MiB) | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` | MIT |

URLs (immutable because they name the commit):

- `https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base-q5_1.bin`
- `https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-small-q5_1.bin`

How the pins were obtained: the SHA-256 and size are what Hugging Face's own file listing reports for the commit (the LFS
object id), and both files were downloaded and hashed independently with `sha256sum` before pinning; the two agree. The
downloads were kept outside the repository and deleted afterwards.

Licence: the Whisper weights are published by OpenAI under the MIT licence, and the whisper.cpp repository that hosts the
converted files states `license: mit`. The model files are data that the app loads; they are not linked into it.

Notes for the person choosing (shown in the model manager):

- `base`: fast and light; good for short phrases in a quiet place. It is what the `full` flavor ships.
- `small`: noticeably more accurate and better when Spanish and English are mixed in one utterance, but about three times the
  size and slower. In-sentence language switching is unreliable with small models; `small` copes better.

## How a model gets onto the device

| Flavor | Bundled | Download | Import from a file |
|---|---|---|---|
| `full` | `base`, copied once on first run | none (no network code, no INTERNET permission) | yes |
| `lite` | none | `base` or `small`, WorkManager, resumable, optional Wi-Fi only | yes |

All models live in app-private storage, `filesDir/models`. A model file only appears there after its SHA-256 was checked, and
it is moved into place atomically, so a model that exists is complete.

### `full`: bundled `base`

`./gradlew assembleFullDebug` runs `fetchBundledModel` (in `app/build.gradle.kts`), which reads `models.json`, downloads the
file once into `$GRADLE_USER_HOME/ultimatekeys-models/<sha256>.bin`, verifies size and SHA-256, and copies it into a generated
asset directory as `assets/models/base.ggml`. A cached, verified copy is reused offline. The asset is listed in `noCompress`, so
it is stored uncompressed in the APK and is byte-identical in every build (the reproducible-build CI job compares two builds).
On first run the app copies it to `filesDir/models/ggml-base-q5_1.bin` (verifying the checksum while copying) and records a
marker; a model the person deletes stays deleted, and the model manager offers to install the bundled one again.

### `lite`: downloader

The model manager enqueues a WorkManager job per model. The job:

- resumes with an HTTP `Range` request from the partial file in `models/.staging`, so an interrupted download (network loss,
  process death, reboot) continues where it stopped;
- runs only on an unmetered connection when "Download only on Wi-Fi" is on (the default), otherwise on any connection;
- reports progress, can be cancelled (which discards the partial file), and retries transient errors with exponential backoff;
- checks the SHA-256 of the finished file and moves it into place atomically. A wrong checksum deletes the file and reports
  an error; nothing unverified is ever used.

Only `https` is accepted, including after redirects. The only network code is `HttpUrlConnectionSource` in
`app/src/lite`; the `INTERNET` permission is declared only in `app/src/lite/AndroidManifest.xml`. The build fails if a
`full` manifest declares `INTERNET` or `ACCESS_NETWORK_STATE`, and fails if the `lite` manifest lacks `INTERNET`
(`verifyFullHasNoInternet`, `verifyLiteHasInternet`).

### Import from a file (both flavors)

"Import a model file" opens the system file picker. The app copies the file into staging while hashing it, then:

1. A file that is not ggml (wrong magic number), is not shaped like a Whisper model (header sanity check), or is too small or
   too large is rejected with a clear message.
2. A file whose SHA-256 equals a catalog model is installed as that model, whatever it is called.
3. Any other Whisper-looking file is "unknown": the app shows its size and checksum and asks for a confirmation ("It could be
   a different or modified model. Install it only if you trust where it came from"). It is installed as `imported-<hash>.bin`
   only if the person confirms.

The header check is a sanity check, not a security measure: it cannot prove that an unknown file is a harmless model. That is why
the confirmation exists. Loading malformed model files is whisper.cpp's responsibility, as it is for any model the person
chooses to use.

## Updating the pins

1. Pick the new commit of `ggerganov/whisper.cpp` on Hugging Face.
2. Download the files into a scratch directory outside the repository, hash them with `sha256sum`, and compare with the LFS
   object ids from `https://huggingface.co/api/models/ggerganov/whisper.cpp/tree/<commit>`.
3. Edit `models.json` (commit, URLs, `bytes`, `sha256`) and this page. `ModelCatalogTest` checks the file's shape; the build and
   the app both fail loudly on a checksum that does not match the bytes.
4. Delete the scratch downloads.
