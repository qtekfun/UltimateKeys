# Progress

## Blockers
- None. Signing secrets (`UK_*`) are pending on the owner (see `docs/HUMAN_TASKS.md`); not a blocker.

## Current phase / branch / open PR
Phase 2 done on branch `phase/2-suggestions` (PR #4, CI green). Next: squash-merge, release v0.3.0, then Phase 3 on `phase/3-style-engine`. v0.1.0 and v0.2.0 are released.

## Last completed task
2.8 and 2.8b. Engine enabled after raising the vendored dictionary size limits (ADR 0008 update). Also added the configurable bottom margin (user request after testing 0.2.0).

## Next task
Release v0.3.0 (docs/CI_CD.md "Making a release"), then Phase 3 task 3.1. Pending human checks: docs/HUMAN_VERIFICATION.md (2.9).

## Build & check commands
`./gradlew spotlessApply check assembleFullDebug assembleLiteDebug`
Full local gate: `./gradlew spotlessCheck check assembleDebug assembleRelease` (check includes lint, detekt, unit tests, koverVerify, verifyFullHasNoInternet).

## Decisions since last update (link ADRs)
- Repo settings applied via `gh api`: squash-merge only, auto-delete head branches.
- Tooling on this machine: JDK 21 (Temurin), Android SDK at `~/Android/Sdk`.
- Release cert SHA-256 (from owner): 59989c4961dc6f7b8c7534491180640416af454b2c0860e490fbc31d7fac98bd (use in fdroid AllowedAPKSigningKeys, no space)
- ADRs 0001-0006 in docs/adr (manual DI, toolchain pins).
- `originalityCheck` task is due in Phase 3 (3.10); CI_CD lists it in `check` from then.
- Owner rule (updated 2026-10-08): NEVER use the connected Pixel 8 (no adb install/ime/tests). Use CI emulator and HUMAN_VERIFICATION.md. Avoid /tmp.
- Use `/tmp/.../scratchpad/regen.sh`-style clean-home run to regenerate `gradle/verification-metadata.xml` whenever dependencies change (include `assembleFullDebugAndroidTest`).
