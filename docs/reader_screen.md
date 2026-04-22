# Reader Screen Guide

Use this guide before changing reader restoration, progress saving, TOC behavior, overscroll navigation, controls, or chapter rendering.

## Read Order

1. `feature/reader/ReaderScreen.kt`
   - State owner and effect coordinator.
2. `feature/reader/ReaderScreenContracts.kt`
   - Contract map and theme helpers.
3. One focused helper/rendering file only if needed:
   - `ReaderScreenChrome.kt`
   - `ReaderScreenControls.kt`
   - `ReaderChapterContent.kt`
   - `ReaderControlsSections.kt`
   - `ReaderControlsWidgets.kt`
   - `ReaderVerticalScrubber.kt`

## File Boundaries

- `ReaderScreen.kt`
  - Owns `currentChapterIndex`, `chapterElements`, restoration flags, progress saving, and navigation actions.
- `ReaderScreenContracts.kt`
  - Owns reader contract types, theme helpers, and chrome surface contracts.
- `ReaderScreenChrome.kt`
  - Owns drawer, top bar, overlays, and reader shell layout.
- `ReaderScreenControls.kt` and split helpers
  - Own controls sheet, scrubber support, chapter rendering, and selection widgets.

## Critical State Machine

### Chapter Load Flow

1. `currentChapterIndex` changes.
2. `ReaderScreen.kt` loads chapter content through `parser.parseChapter(...)` on IO.
3. `chapterElements` updates and drives recomposition.
4. Adjacent chapter prefetch runs as child work of the active load effect.

### Restoration Flow

1. Wait for the `LazyColumn` to finish measuring the new chapter content.
2. Use `snapshotFlow` to wait until item count matches the loaded content.
3. Restore either saved progress or the correct edge position for sequential navigation.
4. Call `scrollToItem(...)`.
5. Keep the `delay(100)` settle step.
6. Set `isInitialScrollDone = true` only after restoration fully settles.

### Progress Save Flow

1. Observe `firstVisibleItemIndex` and `firstVisibleItemScrollOffset`.
2. Gate writes behind `isInitialScrollDone` and `isRestoringPosition`.
3. Keep the `delay(500)` debounce before saving `BookProgress`.

### Navigation Modes

- Initial open:
  - restoration uses saved progress.
- TOC selection:
  - manual jump to the top of the target chapter.
- Numeric jump:
  - reset restoration and land at the top of the target chapter.
- Sequential next/prev:
  - force the correct top or bottom landing position and do not restore stale saved progress.
- Scrubber interaction:
  - keeps `isInitialScrollDone = true` so progress saving does not fight active manual scrolling.

## Do Not Change Accidentally

- `isInitialScrollDone`
- `isRestoringPosition`
- the `delay(100)` restoration settle step
- the `delay(500)` save debounce
- overscroll release behavior
- the rule that back unwinds overlay layers before leaving the reader

## Back Layer Order

- TOC drawer
- text-selection session
- reader controls/settings
- exit reader

## Performance Notes

- Reader setting sliders should preview locally and persist only on settle, not on every drag sample.
- Keep scroll-driven UI signals as narrow state reads so the state owner does not recompose more than necessary.
- Keep selection scoped to composed text items; do not wrap the whole chapter `LazyColumn` in one broad `SelectionContainer`.
