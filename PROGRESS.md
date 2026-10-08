# Progress

## Blockers
- None. Signing secrets (`UK_*`) are pending on the owner (see `docs/HUMAN_TASKS.md`); not a blocker.

## Current phase / branch / open PR
Phase 3 — Style engine, branch `phase/3-style-engine` (no PR yet). v0.1.0, v0.2.0 and v0.3.0 are released (v0.3.0 Release workflow succeeded with both APKs).

## Last completed task
Phase 2 fully (2.9 is a human check, listed in docs/HUMAN_VERIFICATION.md). Also done early: 9.1 launcher icon, configurable bottom margin.

## Next task
3.1 `:style` schema (versioned, Kotlin serialization) with every parameter in SPEC.md section 6.

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
