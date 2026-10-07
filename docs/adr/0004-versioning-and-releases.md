<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0004: Versioning and releases

Status: accepted

## Context

The sibling UltimateDeck already has a proven model.

## Decision

Mirror UltimateDeck: appVersion in gradle.properties, derived versionCode ((MAJOR*10000 + MINOR*100 + PATCH)*100 + N, N=99 for finals), tag-triggered release workflow, hand-written Keep a Changelog, reproducible builds, F-Droid metadata in fdroid/. Branch protection requires CI / check; merges are squash-only.

## Consequences

Each phase ends with a tag. Release signing needs the UK_* secrets; without them the workflow fails with a clear message.
