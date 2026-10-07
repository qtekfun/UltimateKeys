<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Contributing

## Ground rules

- **Free software only.** No Google Play Services, Firebase, analytics, crash reporters or non-free dependency. A dependency's license must be compatible with GPL-3.0-or-later and F-Droid.
- **No telemetry**, and no network code outside the `lite` model downloader.
- **Original code and design.** Do not copy code from other keyboards; do not name other keyboard products in presets, strings or docs.
- Every visible string in `strings.xml` (English in `values/`, Spanish in `values-es/`).
- SPDX header in every source file.

## Workflow

1. Branch from `master`; Conventional Commits (`feat:`, `fix:`, `docs:`, ...).
2. Run `./gradlew spotlessApply check` before pushing.
3. Add user-visible changes under `## [Unreleased]` in `CHANGELOG.md`.
4. Open a PR; CI must be green. PRs are squash-merged.
