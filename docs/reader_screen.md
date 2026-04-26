# Reader Screen Guide

Use this guide before changing reader restoration, progress saving, TOC behavior, overscroll navigation, controls, or chapter rendering.

## Read Order

1. `feature/reader/ReaderScreen.kt`
   - Thin public reader boundary that mounts the internal shell.
2. `feature/reader/internal/shell/ReaderFeatureShell.kt`
   - State owner and effect coordinator.
3. `feature/reader/internal/shell/ReaderFeatureShellCallbacks.kt`
   - Callback wiring between shell-owned state and `ReaderScreenChrome`.
4. `feature/reader/ReaderScreenContracts.kt`
   - Contract map and theme helpers.
5. One focused helper/rendering file only if needed:
   - `feature/reader/internal/shell/ReaderScreenEffects.kt`
   - `feature/reader/internal/shell/ReaderScreenHelpers.kt`
   - `feature/reader/internal/ui/ReaderScreenChrome.kt`
   - `feature/reader/internal/ui/ReaderScreenControls.kt`
   - `feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`
   - `feature/reader/internal/runtime/epub/ReaderChapterSelectionHost.kt`

## File Boundaries

- `ReaderScreen.kt`
  - Public feature entry only. It should stay thin.
- `internal/shell/ReaderFeatureShell.kt`
  - Owns `currentChapterIndex`, `chapterElements`, restoration flags, progress saving, TOC actions, overscroll navigation, controls visibility, and back-layer order.
- `internal/shell/ReaderFeatureShellCallbacks.kt`
  - Owns the translation from shell state into `ReaderChromeCallbacks`, including selection-session invalidation rules and settings-preview/persist wiring.
- `ReaderScreenContracts.kt`
  - Owns reader contract types, theme helpers, and chrome surface contracts.
- `internal/ui/ReaderScreenChrome.kt`
  - Owns drawer, top bar, overlays, and reader shell layout.
- `internal/ui/ReaderScreenControls.kt` and split helpers
  - Own the controls sheet, scrubber support, and reader-local chrome widgets.
- `internal/runtime/epub/EpubReaderRuntime.kt`
  - Owns the single active EPUB chapter runtime.
- `internal/runtime/epub/ReaderChapterSelectionHost.kt`
  - Owns the custom reader selection stack: document mapping, visible-layout registry, word snapping, handles, copy/define/translate actions, tap-to-dismiss, and the lookup sheet handoff.

## Critical State Machine

### Chapter Load Flow

1. `currentChapterIndex` changes.
2. `ReaderFeatureShell.kt` loads chapter content through `parser.parseChapter(...)` on IO.
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
2. Gate writes behind `isInitialScrollDone`, `isRestoringPosition`, and the custom-selection handle-drag flag.
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
- Keep selection chapter-local and reader-owned; selection state lives in `ReaderChapterSelectionHost`, while visible text sections only register layouts and paint highlight slices.
- Do not reintroduce `SelectionContainer`, `LocalTextToolbar`, or clipboard-driven selection state as the source of truth.
- The active reader runtime is now a single plugin-owned EPUB runtime; do not reintroduce hidden engine branching or a user-facing engine selector.
