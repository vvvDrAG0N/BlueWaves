# AI Debug Guide

This guide maps common bug types to the current post-refactor file layout.

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
