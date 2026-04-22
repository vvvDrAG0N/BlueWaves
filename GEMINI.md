## Foundation

- Before performing any task or analysis, read `AGENTS.md`.

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

## Recommended Workflow

- Read `AGENTS.md`, `docs/README.md`, and `docs/project_graph.md` first.
- If `graphify-out/GRAPH_REPORT.md` exists, read it before opening raw files.
- If scope is unclear, run `graphify query "<question>" --budget 800-1500`.
- Read only the runtime docs directly relevant to the task after the graph narrows the area.
- When handing implementation to Codex or another agent, pass the goal, scope, constraints, verification steps, and open questions explicitly.

## Guardrails

- Do not propose broad architectural rewrites unless the user explicitly asks for them.
- Do not override `AGENTS.md` or canonical runtime docs with model-specific preferences.
- Do not scan large swaths of source when the graph or focused docs already answer the routing question.
- If you discover stale repo memory, fix it close to the affected workflow when scope allows.
