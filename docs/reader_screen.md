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

- `ReaderScreenContracts.kt`
  - Owns reader theme helpers, `TocSort`, and the `ReaderChromeState` / `ReaderChromeCallbacks` bundles.

- `ReaderScreenChrome.kt`
  - Owns the TOC drawer, chapter surface shell, top bar, bottom bar mounting, overscroll prompts, and scroll-to-top FAB.

- `ReaderScreenControls.kt`
  - Owns chapter element rendering, bottom settings controls, scrubber UI, and reader theme buttons.

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
