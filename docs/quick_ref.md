# Quick Reference for AI Agents (Blue Waves)

- **State Source of Truth**: `data/settings/SettingsManager.kt` via Jetpack DataStore. UI collects via Flow as Compose state.
- **Entry Files**: `MainActivity.kt` is bootstrap only. Top-level app logic now lives in `app/AppNavigation.kt`.
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
