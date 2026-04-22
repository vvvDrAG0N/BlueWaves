# Agent Memory Schema

This folder is the non-default continuity layer for Blue Waves agents.

Read it only after the canonical runtime docs when you need:
- recent step continuity
- concrete queued follow-up work
- a clue about what the last agent actually did

Do not use this folder as the canonical architecture source of truth.
Promote stable rules into `AGENTS.md` or the canonical root docs instead.

## Files

- `step_history.md`
  - Append-only chronological work log.
- `next_steps.md`
  - Current queued future work.

## Step History Rules

Append one entry after any substantial implementation, audit, review, planning pass, or verification cycle.

Required fields:
- Index (sequential # starting at 1)
- Timestamp
- Agent model
- Agent name
- Task goal
- Area or files touched/inspected
- Action taken
- Result
- Verification
- Blockers
- Suggested next step

Template:

```text
## [Index]. YYYY-MM-DD HH:MM
- Agent model:
- Agent name:
- Task goal:
- Area/files:
- Action taken:
- Result:
- Verification:
- Blockers:
- Suggested next step:
```

## Next Steps Rules

Keep only actionable queued work here.

Required fields:
- Goal
- Why it matters now
- Suggested owner/model
- Starting docs/files
- Risks
- Verification target

Template:

```text
## <short next step title>
- Goal:
- Why now:
- Suggested owner/model:
- Starting docs/files:
- Risks:
- Verification target:
```

## General Rules

- Append to `step_history.md`; do not rewrite history.
- Keep `next_steps.md` short and current.
- When a queued item completes, move the outcome into `step_history.md` and remove or update the next-step entry.
- Use repo-relative paths when useful.
