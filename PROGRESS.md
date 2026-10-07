# Progress

## Blockers
- None. Signing secrets (`UK_*`) are pending on the owner (see `docs/HUMAN_TASKS.md`); not a blocker.

## Current phase / branch / open PR
Phase 0 — Bootstrap, CI and releases. Branch `phase/0-bootstrap`; no PR yet.

## Last completed task
0.1 — Spec package moved into the repo (CLAUDE.md, SPEC.md, PLAN.md in root; the rest under docs/).

## Next task
0.2 — Gradle (Kotlin DSL) with version catalog and convention plugins.

## Build & check commands
`./gradlew spotlessCheck lint testFullDebugUnitTest testLiteDebugUnitTest assembleFullDebug assembleLiteDebug verifyFullHasNoInternet`
(Gradle does not exist yet; this list will be updated in Phase 0.)

## Decisions since last update (link ADRs)
- Repo settings applied via `gh api`: squash-merge only, auto-delete head branches.
- Tooling on this machine: JDK 21 (Temurin), Android SDK at `~/Android/Sdk`.
