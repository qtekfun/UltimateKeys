# CLAUDE.md — UltimateKeys

You are building **UltimateKeys**, a modern Android keyboard written from scratch, with a fully configurable look (style engine), a private mode and offline Whisper dictation. It reuses the AOSP native suggestion engine and whisper.cpp as credited third-party dependencies. You work **autonomously from Phase 0 to Phase 10** without waiting for the user between phases.

## Source of truth

Read these before doing anything, in this order:

1. `SPEC.md` — what to build (product + technical requirements).
2. `PLAN.md` — how and in which order (phases, tasks, Definition of Done).
3. `docs/CI_CD.md` — CI, release and signing requirements.
4. `docs/HUMAN_TASKS.md` — what the human owner does; never try to do these yourself.
5. `PROGRESS.md` — current state (create it in Phase 0 if missing).

If SPEC and PLAN disagree, SPEC wins. If something is unspecified, take the most reasonable decision, record it as an ADR in `docs/adr/`, and continue.

## Language

Everything in English: code, identifiers, comments, commit messages, PR titles and bodies, issues, docs, default UI strings. Spanish only as the `values-es` translation.

## Originality (non-negotiable)

- All app code (Kotlin) is written from scratch. Do not copy code from other keyboard apps (HeliBoard, OpenBoard, FlorisBoard, AOSP Java code, etc.). Reading them for understanding is fine; copying is not.
- The only reused code is what `SPEC.md` §2 lists, kept under `third_party/` with licenses, `NOTICE` and `MODIFICATIONS.md`.
- Built-in styles are original designs with our own names. Never reference other keyboard products (Gboard, iOS/Apple, Samsung, SwiftKey…) in presets, strings, identifiers, docs or store texts.
- Only open-licensed fonts and icons; record each in `docs/THIRD_PARTY.md`.

## Autonomy rules

- Do not stop to ask for confirmation between tasks or phases. Keep going until Phase 10 is complete.
- Stop **only** for a hard blocker:
  - a prerequisite from `docs/HUMAN_TASKS.md` is missing and nothing else can progress without it (e.g. no push access to the repo);
  - a decision is irreversible and outside the spec (e.g. changing the license, deleting the repo, force-pushing `master`).
  When blocked, write the blocker at the top of `PROGRESS.md`, open a GitHub issue labeled `blocked`, then continue with any other task that does not depend on it.
- Tasks that need a physical device or human judgment (feel of typing, dictation quality, real-device benchmarks, battery) are **not** blockers: implement everything you can, add a checklist item to `docs/HUMAN_VERIFICATION.md` and move on.
- Missing signing secrets are not a blocker: keep building and merging; the release workflow fails with a clear message until the secrets exist (see `docs/CI_CD.md`).
- If a dictionary or asset has an unclear or incompatible license, do not use it: pick another source, document the choice in `docs/THIRD_PARTY.md`, and continue.

## Work loop (per task)

1. Pick the next unchecked task in `PLAN.md`.
2. Implement it with tests where the task says so.
3. Run locally the check command list kept in `PROGRESS.md` (initially: `./gradlew spotlessCheck lint testFullDebugUnitTest testLiteDebugUnitTest assembleFullDebug assembleLiteDebug verifyFullHasNoInternet`).
4. Commit with a Conventional Commit message (`feat:`, `fix:`, `build:`, `ci:`, `docs:`, `test:`, `refactor:`, `perf:`, `chore:`). One logical change per commit.
5. Tick the task in `PLAN.md` and update `PROGRESS.md`.

## Git and PR loop (per phase)

- Work on a branch `phase/<n>-<slug>` (e.g. `phase/4-private-mode`).
- Push regularly. Open a PR to `master` early as draft; mark ready when the phase's Definition of Done is met.
- Wait for CI: `gh pr checks --watch`. If CI fails, fix and push; never merge red.
- Squash-merge with a Conventional Commit title summarizing the phase: `gh pr merge --squash --delete-branch`.
- Every PR with user-visible changes adds lines under `## [Unreleased]` in `CHANGELOG.md`.
- After the phase PR is merged, cut the phase's release exactly as in `docs/CI_CD.md` → "Making a release" (release PR bumping `appVersion` and the changelog, merge, push tag `vX.Y.Z`). Confirm the Release workflow succeeded (`gh run watch`); if it fails only because signing secrets are missing, note it in `PROGRESS.md` and continue.
- The sibling repo `qtekfun/UltimateDeck` (public) is the reference for CI, release, signing, changelog and F-Droid setup: mirror it.
- Never force-push `master`. Never move or delete published tags.

## Resume protocol

Your context may be compacted or the session may restart. `PROGRESS.md` must always let a fresh session continue without re-reading the whole repo:

```
# Progress
## Blockers
## Current phase / branch / open PR
## Last completed task
## Next task
## Build & check commands
## Decisions since last update (link ADRs)
```

On start or resume: read `PROGRESS.md`, `git status`, `git log -5`, `gh pr list`, then continue from "Next task".

## Third-party hygiene

- Vendored AOSP engine: `third_party/aosp-latinime/` with `LICENSE`, `NOTICE`, `UPSTREAM_COMMIT` (the pinned AOSP commit) and `MODIFICATIONS.md` listing every changed file and why.
- whisper.cpp: git submodule pinned to a release tag.
- Keep our code out of `third_party/` and third-party code out of our modules.

## Quality bar

- Kotlin, coroutines + Flow, no blocking work on the main thread. Typing latency targets in `SPEC.md` §12 are requirements, not wishes.
- No analytics, crash reporters, ads, or network libraries. The only network code lives in the `lite` model downloader.
- Coverage ≥ 80% on Kotlin modules (vendored code excluded); 100% on private-mode rules.
- Formatting via Spotless + ktlint (Kotlin) and clang-format only on our own C++ glue.
- Do not add a dependency without a one-line justification in the PR body; FOSS and F-Droid-compatible only.

## Things you must never do

- Commit model files, keystores, passwords or `local.properties`.
- Add Google Play Services, Firebase or any proprietary SDK.
- Use proprietary fonts, icons or images.
- Disable a failing test to get CI green.
