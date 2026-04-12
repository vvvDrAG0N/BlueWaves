# Reader Flow & State Management

The `ReaderScreen` in `feature/reader/ReaderScreen.kt` is the core of the application, managing a complex interaction between the file system, persistence, and the UI.

Path note after the reader split:
- `ReaderScreen.kt` now owns state/effects and restoration behavior.
- `ReaderScreenContracts.kt` holds shared reader contracts and theme helpers.
- `ReaderScreenChrome.kt` holds the TOC drawer and shell overlays.
- `ReaderScreenControls.kt` holds reader controls, scrubber UI, and chapter element rendering.

## 1. Chapter Loading Flow

1. **Trigger**: `currentChapterIndex` changes through TOC selection, restoration, or gesture navigation.
2. **Action**: A `LaunchedEffect` triggers `parser.parseChapter()` on an IO thread.
3. **Result**: A list of `ChapterElement` values is stored in state, which triggers recomposition.
4. **Optimization**: Adjacent chapters are pre-parsed on `Dispatchers.IO`.

## 2. Position Restoration Flow

Warning: Modifying the flags or delays described in this flow will break position restoration.

1. **Wait for Layout**: A `snapshotFlow` waits until `totalItemsCount` matches `chapterElements.size`.
2. **Restore**:
   - If navigation was gesture-driven, scroll to the top or bottom of the next chapter as needed.
   - Otherwise, read `scrollIndex` and `scrollOffset` from DataStore and apply them through `listState.scrollToItem()`.
3. **Lock**: Set `isInitialScrollDone = true` only after the restoration sequence fully settles.

## 3. Position Saving Flow

1. **Observation**: A `LaunchedEffect` watches `firstVisibleItemIndex` and `firstVisibleItemScrollOffset`.
2. **Debounce**: A `delay(500)` prevents excessive writes during active scrolling.
3. **Commit**: The position is written back to DataStore as `BookProgress`, keyed by the book ID.

## 4. Navigation Gestures

- **Detection**: A `NestedScrollConnection` captures overscroll deltas near list boundaries.
- **Threshold**: Once `verticalOverscroll` exceeds the configured threshold, a chapter transition is prepared.
- **Execution**: On release, the next or previous chapter is loaded and the restoration flow restarts.

## Related Files

- `feature/reader/ReaderScreen.kt`
- `feature/reader/ReaderScreenContracts.kt`
- `feature/reader/ReaderScreenChrome.kt`
- `feature/reader/ReaderScreenControls.kt`
- `data/parser/EpubParser.kt`
- `data/settings/SettingsManager.kt`
- `core/model/LibraryModels.kt`
- `core/model/SettingsModels.kt`
