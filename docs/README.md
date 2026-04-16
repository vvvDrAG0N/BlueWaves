# Docs Index

This directory is the architectural memory for Blue Waves.

## Temporary Product Status

- Active app-shell support is EPUB-only for now.
- EPUB books now have an in-app `Edit Book` flow with a compact two-tab editor for title/author updates, cover replace/remove, TOC-only chapter rename, chapter reordering, range selection tools, and safe text/html chapter insertion.
- PDF-origin books may still exist in the cache/library as deprecated entries, but import/open flows are intentionally blocked while the PDF path is prepared for a larger safe refactor.
- Deprecated PDF internals now live under `data/pdf/legacy` and `feature/pdf/legacy` for that refactor and are documented in `PDF_review.md`.
- Roadmap order is now: `Edit Book` complete, `Selectable Text` next, PDF work later. `TODO_PROMPTS.md` should be treated as a prompt library, not the execution order by itself.

## Start Here

- `project_graph.md`
  - Lowest-token repo-wide entry point.
  - Use this before broad tasks, then jump into `graphify-out/GRAPH_REPORT.md` or `graphify-out/wiki/index.md` if they exist.
- `quick_ref.md`
  - Fastest high-signal summary for agents.
- `architecture.md`
  - Current layered structure and responsibility boundaries.
- `system_overview.md`
  - Runtime view of the app.
- `package_map.md`
  - Package and file ownership after the Phase 1 refactor.

## Reader-Specific

- `reader_flow.md`
  - Chapter loading, restoration, and save lifecycle.
- `ai_mental_model.md`
  - Safety heuristics and validation checklist.
- `known_risks.md`
  - Fragile areas in reader and parser behavior.
- `AI_RISKS.md`
  - High-risk regression registry.

## Tasking And Navigation

- `AI_ENTRY_POINTS.md`
  - Feature entry points and first files/functions to inspect.
- `AI_DEBUG_GUIDE.md`
  - Common bug classes and where to start debugging.
- `ui_rules.md`
  - UI and styling constraints.
- `PROMPT_TEMPLATES.md`
  - Copy-paste starter prompts for bug fixes, features, reviews, and safe refactors.
- `TODO_PROMPTS.md`
  - Ready-to-use implementation prompts for top-level TODO work.
  - Prompt order is not the execution order; check the root `TODO` status first.

## Graphify Artifacts

- `../graphify-out/GRAPH_REPORT.md`
  - Auto-generated whole-project graph summary.
  - Read this before raw files when the task crosses packages.
- `../graphify-out/wiki/index.md`
  - Auto-generated graph wiki for graph-first navigation.
  - Prefer this over scanning multiple large files when the graph already has the answer.

## App Shell Note

- `app_shell_navigation.md`
  - Low-context guide for `AppNavigation.kt`, `AppNavigationStartup.kt`, `AppNavigationOperations.kt`, `AppNavigationLibraryData.kt`, `AppNavigationLibrary.kt`, and `AppNavigationDialogs.kt`.
  - Use this first for library navigation, folder, dialog, and screen-switching tasks to reduce context load.
  - Also points to `app/AppNavigationContracts.kt` when an agent only needs the shell state/callback map.

## Persistence Note

- `settings_persistence.md`
  - Low-context guide for `SettingsManager.kt`, `SettingsManagerContracts.kt`, and `SettingsManagerJson.kt`.
  - Use this first for DataStore-backed settings, folder metadata, and reading progress persistence tasks.

## EPUB Parsing Note

- `epub_parsing.md`
  - Low-context guide for `EpubParser.kt`, `EpubParserBooks.kt`, `EpubParserEditing.kt`, and `EpubParserChapter.kt`.
  - Use this first for import, metadata cache, EPUB mutation, TOC rebuild, chapter parsing, image resolution, and parser safety tasks.

## Reader Screen Note

- `reader_screen.md`
  - Low-context guide for `ReaderScreen.kt`, `ReaderScreenContracts.kt`, `ReaderScreenChrome.kt`, and `ReaderScreenControls.kt`.
  - Use this first for reader restoration, TOC, chrome, scrubber, controls, and chapter rendering tasks.

## Testing Note

- `test_checklist.md`
  - Baseline JVM, Robolectric, and instrumentation coverage map.
  - Use this to see what is already covered before planning new tests.
- `PROMPT_TEMPLATES.md`
  - Includes `Unit Test` and `Instrumentation Test` starter prompts plus the feature test-selection rule.
- `PDF_review.md`
  - Archived context for the deprecated PDF path and the next safe-refactor pass.

## AI Workflow Note

- `ask_mode_prompt_rules.md`
  - Rulebook for generated implementation and handoff prompts.
  - Use this when creating prompts for another agent or for a later execution pass.
- `TODO_PROMPTS.md`
  - Ready-to-use prompts for the current top-level TODO tracks.
  - Keep this aligned with the root `TODO` execution order.
- `agent_memory/README.md`
  - Shared-memory entry point for multi-agent collaboration.
  - Use this when the task needs durable decisions, handoffs, reusable debug lessons, or open-question tracking across sessions.
- `agent_memory/decision_log.md`
  - Append-only record of stable workflow and architecture decisions discovered by agents.
- `agent_memory/handoffs.md`
  - Shared work ledger for active and recently completed handoffs.
- `agent_memory/debug_lessons.md`
  - Reusable debugging lessons, command gotchas, and high-signal fixes.
- `agent_memory/open_questions.md`
  - Unresolved decisions that should not be silently guessed by later agents.
- `../GEMINI.md`
  - Repo-specific Gemini companion guide for planning, scoping, and review work.
- `../CODEX.md`
  - Repo-specific Codex companion guide for implementation, debugging, and verification work.
