<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# UltimateKeys

A modern, private, fully offline Android keyboard with a configurable look and offline voice dictation.

> Status: early development (0.x). See [CHANGELOG.md](CHANGELOG.md) and [SPEC.md](SPEC.md).

## Goals

- **Fully configurable look**: a style engine where every visual aspect is a parameter; share styles as `.ukstyle` files.
- **100% offline**: typing, suggestions and dictation never need the network.
- **Private by design**: private mode, no telemetry, analytics or cloud.
- **Offline dictation** with whisper.cpp.
- Spanish and English, mixed typing without manual switching.

## Flavors

| Flavor | Model | Network permission |
|---|---|---|
| `full` | `base` model bundled | none (not declared) |
| `lite` | downloaded or imported | only for the model downloader |

## Build

JDK 21 and the Android SDK are required.

```sh
git clone --recurse-submodules https://github.com/qtekfun/UltimateKeys
./gradlew check assembleFullDebug assembleLiteDebug
```

Releases are made by pushing a version tag; see [RELEASING.md](RELEASING.md).

## Credits

Third-party components are listed in [docs/THIRD_PARTY.md](docs/THIRD_PARTY.md).

## License

GPL-3.0-or-later. Third-party components keep their own licenses.
