# Codex Guide

## Foundation

- Before performing any task or analysis, read `AGENTS.md`.

## Best Fit

- Use Codex as the primary execution agent for implementation, debugging, focused refactors, tests, and verification.
- Treat planning input from other agents as guidance, not a substitute for checking the current repo state.

## Recommended Workflow

- Read `AGENTS.md`, `docs/README.md`, and `docs/project_graph.md` first.
- If `graphify-out/GRAPH_REPORT.md` exists, read it before opening broad raw-file sets.
- If scope is unclear or crosses packages, run `graphify query "<question>" --budget 800-1500`.
- Read only the focused runtime docs needed for the task.
- Edit the smallest correct surface, verify the affected flow, and update canonical docs when behavior or ownership changes.

## Guardrails

- Keep diffs surgical unless the user explicitly approves a broader refactor.
- Protect reader restoration, parser safety, and DataStore shape unless the task explicitly targets them.
- Use `core/debug/AppLog.kt` for retained diagnostics.
- After documentation or structural changes, refresh graphify with `python scripts/check_graph_staleness.py --rebuild`.
