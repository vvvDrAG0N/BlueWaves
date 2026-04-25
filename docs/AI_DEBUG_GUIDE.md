# AI Debug Guide

Use this file to route common bug types to the smallest useful file set.

## Start Small

1. Read `docs/project_graph.md`.
2. Read `graphify-out/GRAPH_REPORT.md` if the bug crosses packages.
3. Read one focused area doc before opening multiple raw files.

## Symptom Routing

### Builder Routes The Wrong Screen

- Start with `docs/app_shell_navigation.md`.
- Then inspect `app/AppRoute.kt`, `app/AppFeatureRegistry.kt`, `app/AppNavigation.kt`, and the owning `*LegoPlugin.kt`.

### Scroll Position Not Restored

- Start with `docs/reader_screen.md`.
- Then inspect `feature/reader/ReaderScreen.kt`.
- Verify `isInitialScrollDone`, `isRestoringPosition`, `snapshotFlow`, and the `delay(100)` settle step.

### Folder Order Or Visibility Desync

- Start with `docs/app_shell_navigation.md`.
- Then inspect `feature/library/internal/LibraryFeatureData.kt`, `feature/library/internal/LibraryFeatureContent.kt`, and `data/settings/SettingsManager.kt`.

### Duplicate Book Detection Is Wrong

- Start with `docs/epub_parsing.md`.
- Then inspect `data/parser/EpubParserBooks.kt` and confirm `MD5(uri + fileSize)` still drives identity.

### Chapter Images Not Loading

- Start with `docs/epub_parsing.md`.
- Then inspect `data/parser/EpubParserChapter.kt` for ZIP lookup and `normalizePath()`.

### Edit Book Save Or Reopen Is Wrong

- Start with `feature/editbook/EditBookLegoPlugin.kt` and `docs/epub_parsing.md`.
- Then inspect `data/parser/EpubParserLookup.kt`, `data/parser/EpubParserEditing.kt`, and `feature/editbook/EditBookScreen.kt`.

### Settings, Theme, Or Progress Persistence Is Wrong

- Start with `docs/settings_persistence.md`.
- Then inspect `data/settings/SettingsManagerContracts.kt` and `data/settings/SettingsManager.kt`.

### Disabled PDF Behavior Reappears

- Start with `docs/app_shell_navigation.md`.
- Then inspect `app/AppNavigation.kt`, `app/AppFeatureRegistry.kt`, and `feature/pdf/legacy/PdfLegacyLegoPlugin.kt`.
- Treat `docs/legacy/PDF_review.md` as opt-in context only.
