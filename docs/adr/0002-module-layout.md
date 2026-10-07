<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0002: Module layout

Status: accepted

## Context

SPEC §11 defines the module split. The Gradle build must stay fast and the boundaries testable.

## Decision

One Gradle module per SPEC §11 (app, core, ime, layouts, style, engine, dictionaries, privacy, clipboard, emoji, voice, voice-models; gesture is added in Phase 10). Product flavors (full/lite) are defined in :app; flavor-specific code lives in :app source sets. Shared build config lives in convention plugins in build-logic/ (uk.android.library, uk.android.application, uk.android.compose, uk.android.native, uk.quality).

## Consequences

Cross-module APIs are interfaces (SuggestionEngine, SpeechTranscriber, StyleRepository, PrivacyPolicy). Modules needing flavor behavior (voice-models) expose it through interfaces implemented in :app.
