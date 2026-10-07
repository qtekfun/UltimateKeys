<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0001: Written from scratch, reusing only the AOSP native engine

Status: accepted

## Context

Good suggestions and autocorrect take years to tune. The product must still be original and GPL-3.0 compatible.

## Decision

All Kotlin/IME code is written from scratch. Only the Apache-2.0 AOSP LatinIME native suggestion engine and whisper.cpp (MIT) are reused, kept separate under third_party/ with licenses, NOTICE and MODIFICATIONS.md.

## Consequences

Credits in README, docs/THIRD_PARTY.md and in-app. The engine JNI glue is adapted to our Kotlin classes and every change is recorded.
