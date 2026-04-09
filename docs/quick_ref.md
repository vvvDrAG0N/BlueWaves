# Quick Reference for AI Agents (Blue Waves)

- **State Source of Truth**: SettingsManager (Jetpack DataStore). UI collects via Flow as Compose State.
- **Sacred Reader Flags**: `isInitialScrollDone` and `isRestoringPosition`. Do not alter their logic without full regression testing of position restoration.
- **Magic Delay**: The `delay(100)` in ReaderScreen restoration exists to allow LazyColumn layout measurement. Do not remove or reduce without understanding layout timing.
- **IO Dispatcher**: All file operations and EPUB parsing must be wrapped in `withContext(Dispatchers.IO)`.
- **Image Path Resolution**: Always use `normalizePath()` in EpubParser for img src attributes. EPUBs frequently use relative paths with `..` and `OEBPS/` or `OPS/` roots.
- **Book ID Generation**: Book IDs are MD5 hash of URI + file size. Changing this algorithm will orphan all user reading progress and cached covers.
- **Forced LTR**: The app enforces `LayoutDirection.Ltr` via `CompositionLocalProvider`. This is intentional for consistent reading gestures. Do not remove.
