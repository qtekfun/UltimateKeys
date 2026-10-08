# Progress

## Blockers
- None. Release signing secrets are in place and the published APKs are signed with the owner's certificate.

## Current phase / branch / open PR
Phases 0 to 10 are merged on `master`. Released: v0.1.0 to v0.9.2, v1.0.0-rc.1 (pre-release) and v1.0.0. Gesture typing shipped inside 1.0.0 (the plan's v1.1.0 is only needed for later fixes).

## Last completed task
9.6 (1.0.0). Everything the plan lists as automatable is done.

## Next task
Human items only: the device checks in `docs/HUMAN_VERIFICATION.md` (typing feel, autocorrect, dictation quality, gestures, TalkBack, performance numbers on a real phone), and the F-Droid submission (9.7, parked by the owner). Fix what those checks find and release patch versions (1.0.x) or v1.1.0.

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
- Regenerate `gradle/verification-metadata.xml` whenever dependencies change: run `./gradlew --write-verification-metadata sha256 clean check assembleDebug assembleRelease assembleFullDebugAndroidTest` with a clean `GRADLE_USER_HOME` under `~/.cache`, then delete it.
- Memory: several Gradle daemons from other projects can exhaust RAM; run builds with `--max-workers=2` when the machine is busy.
