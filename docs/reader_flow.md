# Reader Flow & State Management

The `ReaderScreen` is the core of the application, managing a complex interaction between the file system, persistence, and the UI.

## 1. Chapter Loading Flow
1. **Trigger**: `currentChapterIndex` changes (via TOC or Navigation).
2. **Action**: `LaunchedEffect` triggers `parser.parseChapter` on an IO thread.
3. **Result**: A list of `ChapterElement` is returned and stored in state, triggering a UI recomposition.
4. **Optimization**: Adjacent chapters (index-1 and index+1) are pre-parsed and cached in memory.

## 2. Position Restoration Flow
`Warning: Modifying the flags or delays described in this flow will break position restoration. Review known_risks.md for failure scenarios.`

This is a multi-step "Sync" process to ensure the user returns to their exact spot:
1. **Wait for Layout**: A `snapshotFlow` waits until `totalItemsCount` matches the `chapterElements.size`.
2. **Restore**:
    - If `isGestureNavigation` (Next/Prev chapter), it scrolls to the top (or bottom if going backward).
    - Otherwise, it reads `scrollIndex` and `scrollOffset` from DataStore and applies them via `listState.scrollToItem`.
3. **Lock**: `isInitialScrollDone` is set to true, enabling the "Save" logic.

## 3. Position Saving Flow
1. **Observation**: A `LaunchedEffect` monitors `firstVisibleItemIndex` and `firstVisibleItemScrollOffset`.
2. **Debounce**: A 500ms delay prevents excessive writes to DataStore during active scrolling.
3. **Commit**: The current position is saved back to DataStore, keyed by the book's MD5 ID.

## 4. Navigation Gestures
- **Detection**: A `NestedScrollConnection` captures overscroll deltas at the list boundaries.
- **Threshold**: Once `verticalOverscroll` exceeds `80.dp`, a chapter transition is prepared.
- **Execution**: On finger release, `navigateNext()` or `navigatePrev()` is called, updating the index and restarting the flow.
