# Known Risks & Fragile Areas

This document details technical risks, timing-sensitive logic, and synchronization hazards within the Blue Waves EPUB Reader.

## 1. Position Restoration Race Conditions (`ReaderScreen.kt`)
The synchronization between loading chapter elements and restoring the user's scroll position is the most fragile part of the application.

*   **The Risk**: If `scrollToItem` is called before the `LazyColumn` has fully measured and laid out its items, the scroll will be inaccurate or fail entirely.
*   **The Logic**: The app uses `snapshotFlow` to wait for `totalItemsCount` to match the expected number of elements, followed by a `delay(100)`.
*   **Why it's Risky**: Modifying the delay or the filter conditions can cause the "Jump to Top" bug, where the reader resets to the beginning of the chapter upon opening a book.

`Refer to reader_flow.md Section 2 for the exact step-by-step restoration sequence.`


## 2. Progress Overwrite Hazard (`ReaderScreen.kt`)
The app automatically saves the user's reading position as they scroll.

*   **The Risk**: During initial load or chapter transitions, the `LazyListState` temporarily reports a position of `(0, 0)`.
*   **The Logic**: The `isInitialScrollDone` and `isRestoringPosition` flags act as gates. Progress is only saved when `isInitialScrollDone == true` AND `isRestoringPosition == false`.
*   **Why it's Risky**: Removing or mismanaging these flags will result in the app "forgetting" the user's place by overwriting the correct saved position with `(0, 0)` immediately upon opening a book or turning a page.

## 3. Overscroll Navigation Sensitivity (`ReaderScreen.kt`)
Chapter transitions are handled via a custom `NestedScrollConnection`.

*   **The Risk**: Overscroll deltas are accumulated during a gesture. If not reset or handled exactly on `PointerEventType.Release`, chapters might skip or the UI might jitter.
*   **The Logic**: `verticalOverscroll` is accumulated in `onPostScroll` but only triggers navigation and resets inside a separate `pointerInput` block.
*   **Why it's Risky**: Changes to the UI hierarchy or nested scroll dispatching can break the "pull-to-turn-page" behavior, making it either unresponsive or overly sensitive.

## 4. On-Demand EPUB Parsing (`EpubParser.kt`)
Parsing happens when a chapter is opened, not upfront for the entire book.

*   **The Risk**: Large XHTML chapters or chapters with many embedded images can cause UI stutters if the parsing logic leaks into the Main thread.
*   **The Logic**: `parseChapter` uses a manual `XmlPullParser` loop. Images are read as `ByteArray`.
*   **Why it's Risky**: Heavy regex or complex HTML sanitization inside the parsing loop will directly increase chapter load times. The pre-parsing of adjacent chapters (n-1, n+1) is a performance optimization that must be maintained.

## 5. Metadata Sync & ID Generation (`EpubParser.kt`)
The app identifies books using a hash of their URI and file size.

*   **The Risk**: Inconsistent book IDs.
*   **The Logic**: `bookId` is generated using MD5. This ID is used as the folder name in the cache and as the key for DataStore progress.
*   **Why it's Risky**: Changing the ID generation logic will orphan all existing reading progress and cached covers, effectively wiping the user's library.

## 6. Version Tracking & First-Run Logic (`MainActivity.kt`)
The app tracks versions to show the changelog and handle migrations.

*   **The Risk**: Repeating welcome screens or missing changelogs.
*   **The Logic**: Compares `packageInfo.versionCode` with `lastSeenVersionCode` in DataStore.
*   **Why it's Risky**: Improper handling of the `firstTime` flag alongside the version check can cause the app to show the "Welcome" screen every time it's opened or miss showing the changelog after an update.
