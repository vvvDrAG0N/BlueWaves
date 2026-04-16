# Agent Shared Memory

This folder is the durable shared memory layer for Blue Waves agents.

It exists so collaboration does not depend on transient chat history, one model's private context window, or tool-specific runtime state.

## What Lives Here

Use this folder for shared memory that should survive across sessions and across agents:
- stable workflow decisions
- handoffs and partial progress notes
- reusable debug lessons
- unresolved questions that should not be guessed away

Do not use this folder for:
- canonical architecture rules that belong in `AGENTS.md` or the area docs
- task priority, which belongs in the root `TODO`
- generated structure, which belongs in `graphify-out/`

## Memory Layers

Use these layers on purpose:

1. Canonical project memory
- `AGENTS.md`
- `docs/*.md`
- root `TODO`

2. Shared working memory
- `docs/agent_memory/decision_log.md`
- `docs/agent_memory/handoffs.md`
- `docs/agent_memory/debug_lessons.md`
- `docs/agent_memory/open_questions.md`

3. Generated structural memory
- `graphify-out/GRAPH_REPORT.md`
- `graphify-out/wiki/`

4. Runtime orchestration state
- Microsoft Agent Framework workflow/session state
- in-run agent thread state
- temporary tool/runtime context

Rule:
- Layers 1 and 2 are the durable source of truth for this repo.
- Layer 3 is generated navigation help.
- Layer 4 is useful, but not sufficient as long-term shared memory by itself.

## MAF vs Shared Memory

Microsoft Agent Framework is useful for:
- workflow orchestration
- per-run shared state
- agent threads and conversation continuity
- long-running and human-in-the-loop execution

It is not, by itself, the durable memory contract for this repo.

Why:
- runtime state is scoped to workflow instances, sessions, or configured backing stores
- it is not automatically the same thing as human-readable project memory
- later agents still need stable markdown artifacts they can inspect, diff, review, and update in Git

For Blue Waves, use this rule:
- MAF handles orchestration and per-run state.
- Repo markdown handles durable shared memory.

If we later add a database, MCP memory server, or MAF-backed memory provider, it should mirror or derive from this repo memory contract instead of replacing it blindly.

## Obsidian

Obsidian is useful here.

Use it as the human UI for:
- browsing `docs/agent_memory/`
- reading `docs/`
- navigating backlinks between notes
- browsing `graphify-out/wiki/`

Do not rely on:
- `.obsidian/` config files as the memory source of truth
- local plugin state that agents cannot read
- private notes outside the repo when the knowledge should be shared with future agents

## File Guide

- `decision_log.md`
  - append stable decisions and their rationale
- `handoffs.md`
  - track active work, paused work, and next steps
- `debug_lessons.md`
  - record reusable debugging lessons or command/runtime gotchas
- `open_questions.md`
  - track unresolved decisions that need confirmation or future investigation

## Write Rules

- Prefer short, dated entries over long essays.
- Append instead of rewriting history.
- Link to the relevant repo files when possible.
- Promote mature, architectural knowledge into `AGENTS.md` or the relevant area doc.
- Remove stale handoff entries when the work is truly complete, but keep durable lessons and decisions.
