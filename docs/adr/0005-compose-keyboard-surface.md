<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0005: Compose keyboard surface

Status: accepted

## Context

SPEC §3 asks for Compose with an efficient single-canvas key grid, and a fallback to a custom View if latency targets are missed.

## Decision

Draw the key grid on a single Compose Canvas with custom hit testing; panels and settings use regular Compose. If measurements in Phase 1 or 8 miss the targets, replace only the key grid with a custom View and record it in a new ADR.

## Consequences

No per-key composables. Latency is measured from Phase 1.
