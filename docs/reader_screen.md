# Reader Screen Note

This note is the low-context guide for the reader package after the safe split of `ReaderScreen.kt`.

## Read Order

1. `feature/reader/ReaderScreen.kt`
   - State owner and effect coordinator.
   - Start here for chapter loading, restoration, progress saving, and navigation behavior.
2. `feature/reader/ReaderScreenContracts.kt`
   - Shared reader contract map.
   - Load this file when you only need reader theme helpers, `TocSort`, or the chrome callback/state surface.
3. `feature/reader/ReaderScreenChrome.kt`
   - Drawer, top bar, overscroll indicators, and reader shell layout.
   - Load only for TOC, overlay, or chrome rendering work.
4. `feature/reader/ReaderScreenControls.kt`
   - Bottom controls, scrubber, theme buttons, and chapter element rendering.
   - Load only for settings controls, scrubber, or text/image rendering work.

## File Boundaries

- `ReaderScreen.kt`
  - Owns `currentChapterIndex`, `chapterElements`, restoration flags, and reader navigation actions.
  - Owns the chapter load effect, restoration effect, save-progress effect, and overscroll state machine.

## State Transition Logic (The "Why")

The reader supports three distinct ways to navigate chapters, each interacting differently with the scroll restoration state machine:

1.  **Initial Book Open**: `isInitialScrollDone = false`.
    - *Behavior*: Triggers the restoration effect to look up `savedProgress` and `scrollToItem(savedProgress.scrollIndex)`.

2.  **TOC Selection (`selectTocChapter`)**:
    - *Behavior*: Snaps to the top of the selected chapter.
    - *State Impact*: Sets `skipRestoration = true` and `isInitialScrollDone = true`. This forces a "manual jump" behavior where the restoration effect ignores saved progress and simply scrolls to index 0.

3.  **Go to Chapter / Numeric Jump (`jumpToChapter`)**:
    - *Behavior*: Resets the restoration state machine for a clean "out-of-context" jump.
    - *State Impact*: Sets `isInitialScrollDone = false` and `skipRestoration = true`. This triggers a full "re-restoration" cycle but forces it to the top (index 0) of the target chapter.

4.  **Gesture / Button Navigation (`navigateNext` / `navigatePrev`)**:
    - *Behavior*: Navigates sequentially between chapters.
    - *State Impact*: Sets `isGestureNavigation = true` and `isInitialScrollDone = true`.
    - *Critical Detail*: Forcing `isInitialScrollDone = true` *before* the index changes prevents the restoration LaunchedEffect from looking up "stale" progress from the DataStore for the chapter being navigated into. This ensures you always land at the very top (for next) or very bottom (for prev) of the adjacent chapter.

5.  **Scrubber Interaction**: `isInitialScrollDone = true`.
    - *Why*: Prevents the save-progress effect from fighting with the user's active manual scroll.

### Fragile Interactions with Restoration Logic

*   **Saving Progress Interference**: The `isRestoringPosition` and `isInitialScrollDone` flags must be handled carefully. If `isInitialScrollDone` is false, progress saving is disabled. If `isRestoringPosition` is true, progress saving is disabled.
*   **SnapshotFlow Filtering**: All navigation flows rely on `snapshotFlow` to wait for the `LazyColumn` to finish measuring the newly loaded `chapterElements`. If the `totalItemsCount` check is removed, navigation will often fail or "jump" back to the previous chapter's position.
*   **Restoration Settle Time**: The `delay(100)` in the restoration effect is mandatory to allow the Compose runtime to settle after a `scrollToItem` call before enabling progress saving.

- `ReaderScreenContracts.kt`
  - Owns reader theme helpers, `TocSort`, and the `ReaderChromeState` / `ReaderChromeCallbacks` bundles.
  - Resolves both built-in and saved custom themes into explicit reader foreground/background colors.

- `ReaderScreenChrome.kt`
  - Owns the TOC drawer, chapter surface shell, top bar, bottom bar mounting, overscroll prompts, and scroll-to-top FAB.

- `ReaderScreenControls.kt`
  - Owns chapter element rendering, bottom settings controls, scrubber UI, and reader theme buttons.
  - Theme controls render built-in and saved custom themes from the shared settings model.
  - Text selection handle drags can trigger transient `TextToolbar.hide()` calls; do not treat those callbacks alone as proof that the selection session ended.
  - Do not wrap the chapter `LazyColumn` in one shared `SelectionContainer`; keep selection scoped to composed text items so the reader avoids lazy-layout selection invalidation.

## Do Not Change Accidentally

- `isInitialScrollDone`
- `isRestoringPosition`
- the `delay(100)` restoration settle step
- the `delay(500)` progress-save debounce
- overscroll release behavior
- scrubber handling that forces `isInitialScrollDone = true` during manual user scrolling

## AI Hint

Do not load all reader files by default.

- For restoration or progress bugs: start with `ReaderScreen.kt`.
- For TOC, app bar, overscroll prompt, or drawer work: then open `ReaderScreenChrome.kt`.
- For reader controls, scrubber, theme, or chapter rendering work: then open `ReaderScreenControls.kt`.
