# Codex Guide

## Foundation
- **Mandatory**: Before performing any task or analysis, read `AGENTS.md`. It defines the core philosophy, context priority, and critical safety rules for this project.

## Best Fit
- Use Codex as the primary execution agent for implementation, debugging, right-sized tests, focused refactors, and verification.
- Prefer Codex when the task needs concrete file changes, command execution, or careful regression control.
- Treat planning input from other agents as guidance, not as a substitute for checking the current repo state.

## Recommended Workflow
- Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, and `docs/package_map.md` first.
- If `graphify-out/GRAPH_REPORT.md` exists, read it before opening raw files.
- If scope is unclear or crosses packages, run `graphify query "<question>" --budget 800-1500`.
- Read only the focused area docs needed for the task.
- Edit the smallest correct surface, verify the affected flow, and update docs when behavior or entry points changed.

## Guardrails
- Keep diffs surgical unless the user explicitly approves a broader refactor.
- Protect reader restoration, parser safety, and DataStore shape unless the task explicitly targets them.
- Use `core/debug/AppLog.kt` for retained diagnostics.
- After documentation or structural changes, refresh graphify using the repo's graph-staleness workflow.
