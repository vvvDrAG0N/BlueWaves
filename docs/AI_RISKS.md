# AI Risk Registry & Fragile Areas

This document serves as the critical safety manual for Blue Waves. It identifies "load-bearing" logic where small changes can cause catastrophic regressions in user experience or data integrity.

## 1. Position Restoration Race Conditions (ReaderScreen.kt - V1)
**Risk Level**: CRITICAL
The synchronization between loading chapter elements and restoring the user's scroll position is the most fragile part of V1.

*   **The Risk**: If `scrollToItem` is called before the `LazyColumn` has fully measured and laid out its items, the scroll will be inaccurate or fail entirely.
*   **The Logic**: The app uses `snapshotFlow` to wait for `totalItemsCount` to match the expected number of elements, followed by a `delay(100)`.
*   **Sacred Flags**: 
    - `isInitialScrollDone`: Gates the "Save Progress" effect. If set to `true` too early, it will save `(0,0)` over actual progress.
    - `isRestoringPosition`: Prevents UI flickering or overscroll triggers during the jump.
*   **Constraint**: Any modification to `LaunchedEffect(chapterElements)` must preserve the `snapshotFlow` -> `scrollToItem` -> `delay(100)` -> `flag update` sequence.

## 2. Reading Progress Orphanage (EpubParser.kt - V1)
**Risk Level**: CRITICAL
Books are identified by an MD5 hash of `(URI + FileSize)`.

*   **The Risk**: Inconsistent book IDs.
*   **The Logic**: `bookId` is used as the folder name in cache and as the key for DataStore progress.
*   **Constraint**: This logic is IMMUTABLE. Changing it will orphan all existing progress, bookmarks, and cached covers, effectively wiping the user's library.

## 3. UI Mirroring & Layout Direction (Global)
**Risk Level**: HIGH
The app enforces `LayoutDirection.Ltr` globally.

*   **Description**: The app provides a consistent experience regardless of device locale.
*   **Constraint**: Do not remove `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`. Custom scrubbers and gesture-based navigation are hardcoded for LTR coordinates.

## 4. Theme Contrast Hazards (ReaderScreen.kt / SettingsManager.kt)
**Risk Level**: MEDIUM
The app uses hardcoded hex values (e.g., Sepia `#F4ECD8`) instead of standard Material tokens.

*   **Constraint**: When adding new UI components to the reader, you must manually check the current theme string and apply colors from `getThemeColors()`. Standard Material tokens may not have sufficient contrast.

## 5. JSON Deserialization Fragility (SettingsManager.kt)
**Risk Level**: MEDIUM
Folder structures and sorting preferences are stored as raw JSON strings in DataStore.

*   **Constraint**: There is no schema validation. Ensure all JSON manipulations are wrapped in try-catch blocks and provide sensible defaults to prevent app-wide crashes on startup.

## 6. Titan Engine Stability (V2)
**Risk Level**: HIGH
The native C++ engine handles core layout and reflow.

*   **The Risk**: Main-thread starvation or JNI crashes.
*   **Constraint**: All engine calls for search, parsing, or layout MUST be executed on `Dispatchers.IO`. Native `engine.parseWebnovel` and `engine.searchVolumetric` are performance-critical paths.

## 7. Refactor Split Awareness
**Risk Level**: MEDIUM
Both V1 (Reader/Parser) and V2 (Titan Shell) have been split into Atomic helper files.

*   **Constraint**: Always load the `*Contracts.kt` file first to understand the surface map before editing behavior in the state owners.
