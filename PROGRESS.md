# Progress

## Blockers
- None. Signing secrets (`UK_*`) are pending on the owner (see `docs/HUMAN_TASKS.md`); not a blocker.

## Current phase / branch / open PR
Phase 1 — IME core. Branch `phase/1-ime-core`; PR to be opened. Phase 0 released as v0.1.0.

## Last completed task
1.9 — Smoke test passes on the owner's Pixel 8 (`./gradlew :app:connectedFullDebugAndroidTest`, device only, never emulators locally).

## Next task
Open the Phase 1 PR, merge when CI is green, release v0.2.0 (see CI_CD "Making a release"), then Phase 2.

## Build & check commands
`./gradlew spotlessApply check assembleFullDebug assembleLiteDebug`
Full local gate: `./gradlew spotlessCheck check assembleDebug assembleRelease` (check includes lint, detekt, unit tests, koverVerify, verifyFullHasNoInternet).

## Decisions since last update (link ADRs)
- Repo settings applied via `gh api`: squash-merge only, auto-delete head branches.
- Tooling on this machine: JDK 21 (Temurin), Android SDK at `~/Android/Sdk`.
- Release cert SHA-256 (from owner): 59989c4961dc6f7b8c7534491180640416af454b2c0860e490fbc31d7fac98bd (use in fdroid AllowedAPKSigningKeys, no space)
- ADRs 0001-0006 in docs/adr (manual DI, toolchain pins).
- `originalityCheck` task is due in Phase 3 (3.10); CI_CD lists it in `check` from then.
- Owner rule: run instrumented tests on the connected Pixel 8 only, never on a local emulator (CI keeps its emulator job). The test restores the phone's own keyboard afterwards.
- Use `/tmp/.../scratchpad/regen.sh`-style clean-home run to regenerate `gradle/verification-metadata.xml` whenever dependencies change (include `assembleFullDebugAndroidTest`).
