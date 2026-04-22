# Docs Index

This directory is the canonical runtime-memory set for Blue Waves AI agents.

Use the root docs for:
- runtime architecture
- ownership boundaries
- safety rules
- verification guidance

Do not use the root docs for:
- prompt libraries
- duplicated overview docs
- backlog prompt packs
- historical notes that are not part of the active product surface

## Default Read Order

1. `AGENTS.md`
2. `docs/project_graph.md`
3. `graphify-out/GRAPH_REPORT.md`
4. one focused area doc
5. `docs/ai_mental_model.md` for high-risk logic
6. `docs/test_checklist.md` for verification

## Canonical Root Set

- `project_graph.md`
  - Repo-wide graph-first entry point and task routing map.
- `quick_ref.md`
  - Fastest high-signal summary for agents.
- `architecture.md`
  - Module graph, runtime layers, and ownership boundaries.
- `app_shell_navigation.md`
  - Library and shell routing plus state ownership.
- `settings_persistence.md`
  - DataStore ownership and persistence rules.
- `epub_parsing.md`
  - Parser boundaries and EPUB mutation safety.
- `reader_screen.md`
  - Reader restoration, navigation, and UI boundaries.
- `ui_rules.md`
  - Content-first UI conventions.
- `AI_DEBUG_GUIDE.md`
  - Short symptom-to-owner debug routing.
- `ai_mental_model.md`
  - Safety heuristics, risk register, and validation checklist.
- `test_checklist.md`
  - Short verification map and target test suites.

## Non-Default Areas

- `docs/legacy/`
  - Historical or parked notes. Do not read by default.
- `docs/agent_memory/`
  - Structured step history and next-step continuity. Not part of the default runtime-doc path.

## Authoring Rules

- Merge into an existing canonical doc before creating a new root doc.
- Put parked or historical notes under `docs/legacy/`, not the root.
- Keep root docs short, directive, and AI-agent oriented.
- After meaningful doc or structure changes, run `python scripts/check_graph_staleness.py --rebuild`.
