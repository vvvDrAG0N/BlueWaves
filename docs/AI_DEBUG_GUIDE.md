# AI Debug Guide

This guide maps common bug types to the current post-refactor file layout.

## Graph-First Debugging

Before opening several raw files:
1. Read `docs/project_graph.md`.
2. If present, read `graphify-out/GRAPH_REPORT.md`.
3. Run `graphify query "<bug question>" --budget 900-1200` if the failing path is unclear.

Then open only the area doc and files pointed to by the graph.

## Shared Logging

- Use `core/debug/AppLog.kt` for new diagnostics instead of adding more raw `Log` calls.
- Grep `AppLog.PARSER`, `AppLog.APP_SHELL`, `AppLog.READER`, and `AppLog.SETTINGS` first when looking for high-signal logs.
- Keep debug/info logs for trace points and keep warn/error logs for real fallbacks or failures.

## PDF Support Temporarily Disabled

Primary files:
- `app/AppNavigation.kt`
- `app/AppNavigationPdfLegacy.kt`
- `app/AppNavigationOperations.kt`
- `core/ui/LibraryCards.kt`

Debug steps:
1. Confirm the file picker is still EPUB/ZIP-only.
2. Confirm `importBookIntoLibrary(...)` rejects `BookFormat.PDF`.
3. Confirm `openBook(...)` blocks `sourceFormat == PDF` entries with the deprecation snackbar instead of entering a reader screen.
4. Confirm `AppNavigationPdfLegacy.kt` is the only shell-side owner of legacy PDF work observation.
5. Treat `feature/pdf/legacy/PdfReaderScreen.kt` and `data/pdf/legacy/PdfToEpubConverter.kt` as parked internals unless the task is explicitly about the upcoming refactor.

Common causes:
- Re-enabling a PDF shell path in one place but not the others.
- Library copy still implying conversion/open actions are active.
- Tests or docs assuming PDF is part of the current product surface.

## Scroll Position Not Restored

Primary files:
- `feature/reader/ReaderScreen.kt`
- `data/settings/SettingsManager.kt`

Debug steps:
1. Check `LaunchedEffect(chapterElements)` in `ReaderScreen`.
2. Verify `isInitialScrollDone`, `isRestoringPosition`, and saved progress retrieval from `SettingsManager`.
3. Confirm `snapshotFlow` waits for `totalItemsCount >= chapterElements.size`.
4. Confirm the `delay(100)` still happens after `scrollToItem`.

Common causes:
- `isInitialScrollDone` is set too early.
- `snapshotFlow` fires before all items are laid out.
- The restoration path was changed without preserving the existing sequence.

## Folder Order Or Visibility Desync

Primary files:
- `app/AppNavigation.kt`
- `data/settings/SettingsManager.kt`

Debug steps:
1. Trace the persisted mutation in `SettingsManager` first.
2. Check whether `AppNavigation` is rendering derived folders or temporary drag preview state.
3. Verify folder creation writes both existence and order data in one transaction.
4. Confirm selection mode and move mode consume the same derived folder list.

Common causes:
- Competing UI-local and persisted folder sources of truth.
- Derived folder state missing dependencies on updated settings keys.
- Drag preview state not being cleared or persisted correctly.

## Duplicate Book Detection Is Wrong

Primary files:
- `data/parser/EpubParser.kt`
- `app/AppNavigation.kt`

Debug steps:
1. Inspect the MD5 generation in `parseAndExtract()`.
2. Verify the hash still uses both `Uri` and file size.
3. Inspect `cache/books/` for an existing folder with the same ID.

Common causes:
- File size lookup failure.
- Input changes to the ID generation string.
- Duplicate checks using stale in-memory library state.

## Chapter Images Not Loading

Primary files:
- `data/parser/EpubParser.kt`
- `feature/reader/ReaderScreen.kt`

Debug steps:
1. Check `parseChapter()` ZIP entry resolution.
2. Verify `normalizePath()` against relative image paths like `../Images/img.jpg`.
3. Confirm the parsed image bytes are reaching the image composable.

Common causes:
- Broken relative path normalization.
- ZIP entry lookup only matching one EPUB directory layout.
- Malformed XHTML causing the parser to skip image tags.

## Parser File Split Note

For parser bugs after the safe split:

1. Start with `docs/epub_parsing.md`.
2. Read `data/parser/EpubParser.kt` for the public entry point.
3. For duplicate/import/cache bugs, inspect `data/parser/EpubParserBooks.kt`.
4. For chapter/image/path bugs, inspect `data/parser/EpubParserChapter.kt`.

## Reader File Split Note

For reader bugs after the safe split:

1. Start with `docs/reader_screen.md`.
2. Read `feature/reader/ReaderScreen.kt` for restoration, progress, and navigation behavior.
3. For TOC, top bar, overscroll prompts, or drawer rendering bugs, inspect `feature/reader/ReaderScreenChrome.kt`.
4. For controls, scrubber, theme, or chapter element rendering bugs, inspect `feature/reader/ReaderScreenControls.kt`.

## App Shell File Split Note

For library/app-shell bugs after phase 3:

1. Start with `docs/app_shell_navigation.md`.
2. Read `app/AppNavigation.kt` for state ownership and screen routing.
3. For first-run/version/changelog bugs, inspect `app/AppNavigationStartup.kt`.
4. For import/delete/last-read side-effect bugs, inspect `app/AppNavigationOperations.kt`.
5. For folder-order/sort/drag-preview bugs, inspect `app/AppNavigationLibraryData.kt`.
6. Read `app/AppNavigationLibrary.kt` or `app/AppNavigationDialogs.kt` only if the bug is rendering-specific.

## Overscroll Navigation Not Triggering

Primary files:
- `feature/reader/ReaderScreen.kt`

Debug steps:
1. Inspect the `NestedScrollConnection`.
2. Verify `verticalOverscroll` crosses the threshold.
3. Confirm the release handler resets state and triggers navigation.

Common causes:
- Overscroll threshold regression.
- `verticalOverscroll` not resetting on release.
- Gesture interception changes elsewhere in the layout tree.

## TOC Drawer Does Not Follow Current Chapter

Primary files:
- `feature/reader/ReaderScreen.kt`

Debug steps:
1. Check the `LaunchedEffect` that drives `tocListState`.
2. Verify `currentChapterIndex` is correct when the drawer opens.
3. Ensure TOC items exist before calling `scrollToItem`.

Common causes:
- Effect dependencies no longer include the active chapter.
- TOC list is not ready when the scroll is requested.
- Current chapter index was reset during a chapter load transition.
