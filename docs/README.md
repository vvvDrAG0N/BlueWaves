# Docs Index

This directory is the architectural memory for Blue Waves.

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
  - Ready-to-use implementation prompts for the current top-level TODO features.

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
  - Low-context guide for `EpubParser.kt`, `EpubParserBooks.kt`, and `EpubParserChapter.kt`.
  - Use this first for import, metadata cache, TOC rebuild, chapter parsing, image resolution, and parser safety tasks.

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
# Test change
