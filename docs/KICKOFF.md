# Kickoff prompt

Historical: the prompt used to start the autonomous build. Complete `docs/HUMAN_TASKS.md` first, then open Claude Code in the repo and paste:

---

You are going to build UltimateKeys end to end, autonomously.

1. Read `CLAUDE.md`, `SPEC.md`, `PLAN.md`, `docs/CI_CD.md` and `docs/HUMAN_TASKS.md` completely.
2. Execute `PLAN.md` from Phase 0 to Phase 10 following the work loop and PR loop in `CLAUDE.md`. Do not stop between phases and do not ask me for confirmation; take reasonable decisions and record them as ADRs.
3. The GitHub repository is `qtekfun/UltimateKeys`. Move these package files into the repo root in Phase 0 (they become the project's docs).
4. Keep `PROGRESS.md` up to date after every task so the work can resume at any time.
5. Stop only for the hard blockers defined in `CLAUDE.md`.
6. When Phase 10 is done, give me a final summary: releases published, what is pending in `docs/HUMAN_VERIFICATION.md`, and any open `blocked` issues.

Start now with Phase 0.

---

## If the session stops halfway

Open Claude Code in the repo and paste:

> Read `CLAUDE.md` and `PROGRESS.md`, then resume the plan from the next task. Same rules: no stops between phases.
