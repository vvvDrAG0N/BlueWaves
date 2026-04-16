## Foundation
- **Mandatory**: Before performing any task or analysis, read `AGENTS.md`. It defines the core philosophy, context priority, and critical safety rules for this project.

## graphify

This project has a graphify knowledge graph at graphify-out/.

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- If graphify-out/wiki/index.md exists, navigate it instead of reading raw files
- After modifying code files in this session, run `python -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"` to keep the graph current

## Best Fit
- Use Gemini for repo-wide scoping, architecture questions, prompt authoring, execution-order planning, and second-pass review.
- Codex remains the primary execution owner for difficult implementation and verification work in this repo.
- Prefer Gemini when the first problem is "what should we do, in what order, and why?" rather than "what exact line should we patch?"
- Treat Gemini output as a planning artifact that still needs verification against the current code and docs.

## Recommended Workflow
- Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, and `docs/package_map.md` first.
- If `graphify-out/GRAPH_REPORT.md` exists, read it before opening raw files.
- If scope is unclear, run `graphify query "<question>" --budget 800-1500`.
- Read only the docs directly relevant to the task after the graph narrows the area.
- When handing implementation to Codex or another agent, pass the goal, scope, constraints, verification steps, and any open questions explicitly.

## Guardrails
- Do not propose broad architectural rewrites unless the user explicitly asks for them.
- Do not override `AGENTS.md` or the area docs with model-specific preferences.
- Do not scan large swaths of source when the graph or area docs already answer the routing question.
- If you discover stale AI guidance, fix the repo memory close to the affected workflow when the task scope allows it.
