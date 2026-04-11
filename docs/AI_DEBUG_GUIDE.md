# AI Debug Guide

This guide provides common bug scenarios and exact debugging steps for the Blue Waves EPUB Reader.

---

BUG TYPE: Scroll position not restored when reopening a book

DEBUG STEPS:
1. Check `ReaderScreen.kt` → `LaunchedEffect(chapterElements)`
2. Verify `isInitialScrollDone` flag and `savedProgress` retrieval from `SettingsManager`.
3. Confirm `snapshotFlow` waits for `totalItemsCount >= chapterElements.size`.
4. Check if `delay(100)` is present after `scrollToItem` to allow for layout settling.

COMMON CAUSE:
- `isInitialScrollDone` set to true before the scroll actually completes.
- `snapshotFlow` filter condition fails because items aren't fully measured.
- Missing delay causes a race condition with the Compose layout engine.

---

BUG TYPE: Folder deletion not updating UI

DEBUG STEPS:
1. Trace `SettingsManager.deleteFolder` to ensure DataStore is updated.
2. Check `MainActivity.kt`'s `AppNavigation` for the `refreshLibrary()` call.
3. Verify `folders` derived state in `MainActivity.kt` reacts to `globalSettings.bookGroups` or `folderSorts` changes.

COMMON CAUSE:
- `refreshLibrary()` not called after the mutation in `SettingsManager`.
- The `folders` `remember` block missing a dependency on the updated settings key.

---

BUG TYPE: Book import shows duplicate detection incorrectly

DEBUG STEPS:
1. Check `EpubParser.parseAndExtract` → `generateMD5` logic.
2. Verify the MD5 is based on both URI and File size.
3. Inspect `cache/books/` to see if a folder with that MD5 already exists.

COMMON CAUSE:
- URI format changes (e.g., encoded vs decoded) causing different MD5s for the same file.
- File size read error resulting in "0" or inconsistent values.

---

BUG TYPE: Chapter images not loading

DEBUG STEPS:
1. Check `EpubParser.parseChapter` → `ZipFile` entry resolution.
2. Verify `normalizePath` is correctly handling relative paths (e.g., `../Images/img.jpg`).
3. Check `AsyncImage` in `ReaderScreen.kt` to see if the file path passed is absolute and exists in the cache.

COMMON CAUSE:
- Incorrect base path calculation for images inside the EPUB structure.
- `ZipFile` entry not found due to case sensitivity or missing subfolder prefix.

---

BUG TYPE: Overscroll navigation not triggering

DEBUG STEPS:
1. Check `ReaderScreen.kt` → `nestedScrollConnection` implementation.
2. Verify `onPostScroll` captures enough unconsumed delta to trigger the threshold.
3. Check `pointerInput` for gesture detection interference.

COMMON CAUSE:
- Threshold too high for subtle swipes.
- `verticalOverscroll` state not resetting on `Release`, blocking subsequent triggers.

---

BUG TYPE: TOC drawer not scrolling to current chapter

DEBUG STEPS:
1. Check `ReaderScreen.kt` → `LaunchedEffect` with `tocListState`.
2. Verify `currentChapterIndex` is being passed correctly to `tocListState.scrollToItem`.
3. Ensure the TOC items are fully composed before scrolling.

COMMON CAUSE:
- `currentChapterIndex` update not triggering the `LaunchedEffect`.
- TOC list not yet populated when the scroll command is issued.
