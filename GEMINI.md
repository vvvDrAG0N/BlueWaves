## Foundation

- Before performing any task or analysis, read `AGENTS.md`.
- Treat the current user thread as active context, especially when it contains corrections, product decisions, or file-ownership boundaries.

## graphify

This project has a graphify knowledge graph at `graphify-out/`.

Rules:
- Before answering architecture or codebase questions, read `graphify-out/GRAPH_REPORT.md`.
- If `graphify-out/wiki/index.md` exists, prefer it over broad raw-file scanning.
- After meaningful documentation or structural changes, refresh graphify with `python scripts/check_graph_staleness.py --rebuild`.

## Best Fit

- Use Gemini for repo-wide scoping, architecture questions, execution-order planning, and second-pass review.
- Codex remains the primary execution owner for difficult implementation and verification work.
- Treat Gemini output as a planning artifact that still needs verification against the current repo state.

## Decision Precedence

When sources disagree, use this order:

1. Current user instructions and corrections in the active thread.
2. `AGENTS.md` and canonical runtime docs under `docs/`.
3. Current code and shipped behavior.
4. Targeted recent entries in `docs/agent_memory/step_history.md` and `docs/agent_memory/next_steps.md` when the task touches an active follow-up, a recent decision, or a prior failed path.
5. Roadmap-only docs such as `README.md` and `TODO`.

Rules:

- Never let a stale roadmap item override current code, explicit user direction, or a newer recorded decision.
- If code already implements a behavior, describe the next step as stabilization/polish/integration rather than proposing the behavior as net-new.
- If `README.md`, `TODO`, or older planning text conflicts with the current implementation, call out the mismatch explicitly and prefer the newer source of truth.
- If a prior approach was rejected for product or platform reasons, do not casually re-propose it without saying why the earlier decision no longer applies.

## Recommended Workflow

- Read `AGENTS.md`, `docs/README.md`, and `docs/project_graph.md` first.
- If `graphify-out/GRAPH_REPORT.md` exists, read it before opening raw files.
- If scope is unclear, run `graphify query "<question>" --budget 800-1500`.
- Read only the runtime docs directly relevant to the task after the graph narrows the area.
- If the task is a follow-up, planning revisit, or disagreement about repo memory, read only the relevant recent `step_history.md` or `next_steps.md` entry instead of scanning the whole file.
- When handing implementation to Codex or another agent, pass the goal, scope, constraints, verification steps, and open questions explicitly.
- When another agent is active in parallel, keep a disjoint file scope and state that boundary in the handoff or plan.

## Guardrails

- Do not propose broad architectural rewrites unless the user explicitly asks for them.
- Do not override `AGENTS.md` or canonical runtime docs with model-specific preferences.
- Do not scan large swaths of source when the graph or focused docs already answer the routing question.
- If you discover stale repo memory, fix it close to the affected workflow when scope allows.
- Do not present speculative roadmap ideas as if they are the current product direction.
- Do not re-open settled implementation choices from `step_history.md` unless the user asks to revisit them or current code clearly contradicts the recorded decision.
