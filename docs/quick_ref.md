# Quick Reference for AI Agents (Blue Waves)

- **Graph-First Entry**: Read `docs/project_graph.md` first for repo-wide work.
- **Graph Summary**: If present, read `graphify-out/GRAPH_REPORT.md` before loading large groups of files.
- **Graph Query**: Use `graphify query "<question>" --budget 800-1500` when task scope crosses packages or the entry file is unclear.
- **Graph Rebuild**: Run `python scripts/rebuild_graphify.py` if `graphify-out/` is missing or stale.
- **State Source of Truth**: `data/settings/SettingsManager.kt` via Jetpack DataStore. UI collects via Flow as Compose state.
- **Settings Contract Map**: `data/settings/SettingsManagerContracts.kt` is the fastest way to understand persistence keys, defaults, and model mapping.
- **Entry Files**: `MainActivity.kt` is bootstrap only. Top-level app logic now lives in `app/AppNavigation.kt`.
- **App Shell Contract Map**: `app/AppNavigationContracts.kt` is the fastest way to understand app-shell constants, state bundles, and callback bundles.
- **App Shell Split**: `app/AppNavigation.kt` owns state/effects, `app/AppNavigationStartup.kt` owns startup/version helpers, `app/AppNavigationOperations.kt` owns side-effect helpers, `app/AppNavigationLibraryData.kt` owns pure folder/sort helpers, `app/AppNavigationLibrary.kt` owns library rendering, and `app/AppNavigationDialogs.kt` owns modal surfaces.
- **Low-Token Entry**: For app-shell work, read `docs/app_shell_navigation.md` before loading all app-shell files.
- **Reader Location**: Reader logic lives in `feature/reader/ReaderScreen.kt`.
- **Parser Location**: EPUB parsing lives in `data/parser/EpubParser.kt`.
- **Shared Models**: `EpubBook`, `TocItem`, `ChapterElement`, `GlobalSettings`, and `BookProgress` live under `core/model`.
- **Sacred Reader Flags**: `isInitialScrollDone` and `isRestoringPosition`. Do not alter their logic without full regression testing of position restoration.
- **Magic Delay**: The `delay(100)` in reader restoration exists to allow `LazyColumn` layout measurement. Do not remove or reduce without understanding layout timing.
- **IO Dispatcher**: All file operations and EPUB parsing must be wrapped in `withContext(Dispatchers.IO)`.
- **Image Path Resolution**: Always use `normalizePath()` in `EpubParser` for image paths. EPUBs frequently use relative paths with `..`, `OEBPS/`, and `OPS/`.
- **Book ID Generation**: Book IDs are MD5 of `URI + file size`. Changing this will orphan reading progress and cached covers.
- **Forced LTR**: The app enforces `LayoutDirection.Ltr` for consistent reader gestures. Do not remove.
- **Library State Rule**: Persisted folder state must remain the source of truth. Local drag state should be temporary preview only.
- **Shared Logging**: Use `core/debug/AppLog.kt` for new logs. Keep debug/info logs debug-only and avoid noisy UI/event spam.
- **Feature Test Rule**: New non-trivial features should add the smallest right-sized automated test: JVM for pure logic, Robolectric for Android-aware local behavior, instrumentation for runtime/UI flows. If skipped, explain why and give manual verification.

Parser split note:
- Read `docs/epub_parsing.md` first for parser tasks.
- `EpubParser.kt` is now the public facade only.
- `EpubParserBooks.kt` owns book ID generation plus metadata/TOC/cover cache logic.
- `EpubParserChapter.kt` owns chapter parsing, ZIP entry lookup, and `normalizePath()`.

Reader split note:
- Read `docs/reader_screen.md` first for reader tasks.
- `ReaderScreen.kt` owns loading, restoration, save-progress, and navigation behavior.
- `ReaderScreenContracts.kt` owns the reader contract map and theme helpers.
- `ReaderScreenChrome.kt` owns the TOC drawer, overlays, and top-level reader shell.
- `ReaderScreenControls.kt` owns controls, scrubber UI, and chapter element rendering.
