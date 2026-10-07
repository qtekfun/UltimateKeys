<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0003: Manual dependency injection

Status: accepted

## Context

An InputMethodService has no natural scope for Hilt, and Hilt adds KSP, generated code and slower, less reproducible builds.

## Decision

Manual DI: an AppContainer created in the Application class, exposing interfaces from each module; fakes are passed in tests.

## Consequences

Less boilerplate generation and fewer dependencies for F-Droid. We must keep the container small and explicit.
