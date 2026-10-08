# Progress

## Blockers
- None. Signing secrets (`UK_*`) are pending on the owner (see `docs/HUMAN_TASKS.md`); not a blocker.

## Current phase / branch / open PR
Phases 0-6 and 10 are merged on `master`. Released: v0.1.0 to v0.7.0 (0.6.0 = dictation core, 0.7.0 = clipboard and emoji). Gesture typing (phase 10) is merged but unreleased; the plan reserves v1.1.0 for it, so it ships inside the next release.
In progress, in parallel (one agent each, branches pushed, no PRs yet): Phase 7 `phase/7-models`, Phase 8 (8.3-8.6) `phase/8-polish`, Phase 9 material `phase/9-release-material`. Merge them one at a time into master (squash), expect changelog/strings conflicts, then release v0.8.0 (phase 7).

## Last completed task
Phase 10 (10.1-10.7; 10.8 is a human check). Phase 5 and 6 delivered by agents and merged.

## Next task
Integrate the three agent branches (PR, CI, merge), release v0.8.0 after Phase 7, then 8.1 (performance numbers, APK sizes) and 8.2 (model idle unload), v0.9.0, then Phase 9: 1.0.0-rc.1 and 1.0.0, and v1.1.0 for gestures.

## Build & check commands
`./gradlew spotlessApply check assembleFullDebug assembleLiteDebug`
Full local gate: `./gradlew spotlessCheck check assembleDebug assembleRelease` (check includes lint, detekt, unit tests, koverVerify, verifyFullHasNoInternet).

## Decisions since last update (link ADRs)
- Repo settings applied via `gh api`: squash-merge only, auto-delete head branches.
- Tooling on this machine: JDK 21 (Temurin), Android SDK at `~/Android/Sdk`.
- Release cert SHA-256 (from owner): 59989c4961dc6f7b8c7534491180640416af454b2c0860e490fbc31d7fac98bd (use in fdroid AllowedAPKSigningKeys, no space)
- ADRs 0001-0006 in docs/adr (manual DI, toolchain pins).
- `originalityCheck` task is due in Phase 3 (3.10); CI_CD lists it in `check` from then.
- Owner rule (updated 2026-10-08): NEVER use the connected Pixel 8 or any device (no adb install/ime/tests). Use CI emulator and HUMAN_VERIFICATION.md. Avoid /tmp.
- GitGuardian once reported a false positive on the phase 4 PR (no secret in the diff); it passed on later PRs.
- Agents work in worktrees outside `.claude/worktrees` (ktlint ruleProviders quirk under that path) and must not use `git add -A` (a worktree link was committed once).
- The ADR numbers 0009 (voice), 0012 (clipboard/emoji) and 0020 (gestures) are taken; pick the next free one.
- Use `/tmp/.../scratchpad/regen.sh`-style clean-home run to regenerate `gradle/verification-metadata.xml` whenever dependencies change (include `assembleFullDebugAndroidTest`).
