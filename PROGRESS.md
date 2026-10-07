# Progress

## Blockers
- None. Signing secrets (`UK_*`) are pending on the owner (see `docs/HUMAN_TASKS.md`); not a blocker.

## Current phase / branch / open PR
Phase 2 — Suggestion engine. Branch `phase/2-suggestions` (no PR yet). v0.1.0 and v0.2.0 are released.
Two subagents work in parallel in worktrees: `agent/engine-jni` (2.1-2.3 vendored AOSP engine + JNI) and `agent/dictionaries-mixed` (2.4-2.5 dictionaries + mixed ES/EN merge). Merge their branches into `phase/2-suggestions` when they report.

## Last completed task
2.6 (UI-side) — suggestion strip, autocorrect with backspace undo, next-word prediction wiring, against the `SuggestionEngine` interface (fake engines in tests).

## Next task
Merge the two agent branches; wire the real engine in the service (2.3/2.4); 2.7 user dictionary screen; 2.8 on-device engine test + latency; 2.8b reproducible-build CI job; then PR + release v0.3.0.

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
