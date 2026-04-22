# Quick Reference

- Read `docs/project_graph.md` first for repo-wide work.
- Read `graphify-out/GRAPH_REPORT.md` next when the task crosses packages.
- Then read one focused area doc, not the whole `docs/` tree.

- Runtime boundary:
  - active app shell imports and opens EPUB only
  - PDF-origin entries may still appear in library metadata
  - active PDF open/import remains intentionally disabled

- Module map:
  - `:app` assembles the shell
  - `:core:model` owns shared pure models
  - `:core:ui` owns shared presentation helpers
  - `:data:settings` owns `SettingsManager`
  - `:data:books` owns `EpubParser` and parser-side runtime helpers
  - `:feature:*` owns feature-specific UI and orchestration

- Fast owner files:
  - `MainActivity.kt` for bootstrap only
  - `AppNavigation.kt` for app-shell behavior
  - `SettingsManager.kt` for persisted state
  - `EpubParser.kt` for parser entry points
  - `ReaderScreen.kt` for reader behavior
  - `EditBookScreen.kt` for EPUB edit flow UI

- Sacred invariants:
  - book IDs stay `MD5(uri + fileSize)`
  - `SettingsManager` remains the persisted source of truth
  - `metadata.json` remains the parser cache contract
  - `isInitialScrollDone` and `isRestoringPosition` remain load-bearing
  - `delay(100)` restoration settle time and `delay(500)` save debounce are not casual cleanup targets
  - forced `LayoutDirection.Ltr` stays in place for reader gesture stability

- Size and modularity:
  - Kotlin files stay under 500 lines
  - split around the 350-450 line range or when a second responsibility appears
  - do not solve reuse by feature-to-feature imports

- Doc routing:
  - app shell -> `docs/app_shell_navigation.md`
  - settings/persistence -> `docs/settings_persistence.md`
  - parser/edit-book mutation -> `docs/epub_parsing.md`
  - reader -> `docs/reader_screen.md`
  - high-risk changes -> `docs/ai_mental_model.md`
  - verification -> `docs/test_checklist.md`
