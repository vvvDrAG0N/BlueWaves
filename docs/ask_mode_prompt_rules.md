# Ask Mode Prompt Rules

This file is the rulebook for prompts generated for future agents.

Use it together with:
- `AGENTS.md`
- `docs/README.md`
- `docs/PROMPT_TEMPLATES.md`

## Core Rule

Every generated implementation prompt should tell the next agent:
- what to read first
- what exact outcome is required
- what files or packages are in scope
- what risks must be protected
- how success will be verified

## Required Prompt Shape

A good prompt should usually include:
- `Task`: bug fix, feature, review, safe refactor, unit test, instrumentation test, or docs/workflow task
- `Goal`: one exact outcome
- `Context bootstrap`: the initial docs and graph-first instructions
- `Scope`: allowed files or packages
- `Avoid`: files or systems that should not be touched
- `Constraints`: behavior or architecture rules that must remain true
- `Verification`: exact commands or manual checks

## Context Bootstrap Rule

Default bootstrap block:

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, and `docs/package_map.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before loading raw files.
If the task crosses packages or the entry point is unclear, run `graphify query "<question>" --budget 1200` before opening source files.
Then read only the docs directly relevant to this task.
```

Add focused docs only after the base block is present.

## Prompt Quality Rules

- Use concrete verbs: `fix`, `implement`, `review`, `measure`, `add`, `verify`.
- Name the specific user-visible outcome.
- Prefer explicit acceptance criteria over vague intent.
- Tell the next agent what must stay unchanged.
- Prefer a small, accurate scope over "search the whole repo."

## Token Efficiency Rules

- Do not tell the next agent to open whole packages by default.
- Prefer graph-first routing and low-context area docs.
- If the task is broad, ask the next agent to narrow the file set before editing.
- Use contract-map and area docs when they answer the routing question.

## Task-Specific Rules

### Bug Fix
- Include repro steps.
- Include current wrong behavior.
- Include expected behavior.
- Call out any likely owner files if known.

### Feature
- Include a short user flow.
- Include acceptance criteria.
- Include non-goals when the feature can easily expand.
- Name the smallest appropriate automated test level, or say why docs/manual verification is enough.

### Review
- State that no code changes should be made.
- Ask for findings first, ordered by severity.
- Require file and line references.

### Safe Refactor
- State that behavior must not change.
- Call out files or systems to avoid.
- Require a build or targeted verification at the end.

### Docs Or Workflow Task
- State whether the pass is docs-only or allowed to touch tooling/config.
- If the task updates repo memory, require index updates when practical.
- If the task repairs stale references, require the agent to list what was stale and how it was repaired.

## Handoff Rules

When one agent prepares work for another:
- separate facts from assumptions
- list open questions explicitly
- include exact verification
- keep the prompt short enough that the next agent can act without re-reading unrelated files

Recommended handoff fields:
- `Goal`
- `Why now`
- `Scope`
- `Avoid`
- `Risks`
- `Verification`

## Completion Rule

A generated prompt should lead to a completion message that includes:
- what changed
- what was verified
- any residual risks
- whether docs or graphify were updated
