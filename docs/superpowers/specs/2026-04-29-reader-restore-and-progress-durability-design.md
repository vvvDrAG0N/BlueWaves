# Reader Restore And Progress Durability Design

## Goal

Stabilize the EPUB reader around two confirmed user-facing problems without changing app-shell architecture or persistence schema:

1. recent reader progress can be lost when the app is backgrounded or killed before the debounced save lands
2. reopening a book with saved mid-chapter progress can briefly paint the correct chapter at the wrong top position before the restore jump finishes

## Scope

- EPUB reader progress durability while the reader screen is active
- app background, activity recreation, and process/task-kill reader edge cases
- initial reader-open restore presentation timing
- focused reader test coverage for lifecycle flushes and first-frame restore behavior

Out of scope:

- changing DataStore keys or `BookProgress` schema
- persisting the active `AppRoute` across cold restart
- broad reader runtime rewrites
- selection behavior changes
- parser changes
- PDF behavior changes

## Current Ownership

- `app/src/main/java/com/epubreader/app/AppNavigation.kt`
  - owns the current route in plain Compose memory and currently cold-starts on the library route
- `data/settings/src/main/java/com/epubreader/data/settings/SettingsManager.kt`
  - remains the only persisted source of truth for `BookProgress`
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`
  - owns reader chapter loading, restore timing, progress saves, and back behavior
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenHelpers.kt`
  - owns progress snapshot helpers and chapter loading helpers
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`
  - owns whether the chapter runtime shows loading content or rendered chapter content

## Findings This Design Must Solve

### Finding 1: Background / Kill Progress Gaps

Today, EPUB progress persistence relies on:

- the debounced in-reader save path in `ReaderFeatureShell.kt`
- explicit `saveAndBack()` when the reader exits through the app's own back flow

No dedicated reader lifecycle flush was found on `ON_STOP` or `ON_PAUSE`.

That means:

- if the app stays alive in the background, the in-memory reader state survives and the user returns to the same reader surface
- if the activity/process is recreated later, `AppNavigation.kt` rebuilds from `AppRoute.Library`
- reopening the book restores only the latest persisted `BookProgress`
- recent movement can be lost if the app backgrounds or dies before the debounce writes it

### Finding 2: Reader Open Restore Flicker

Today, the reader chooses the saved chapter before rendering, but the saved item offset is restored only after:

1. chapter content loads
2. the `LazyColumn` mounts
3. measurement completes
4. `scrollToItem(...)` runs

That means the likely visible flicker is:

- correct chapter
- wrong initial top position
- then restore jump

This is a restore-presentation problem, not a wrong-chapter-open problem.

## User-Facing Behavior For This Pass

### Background / Resume

- If the app is backgrounded and survives, the user should return to the same live reader state.
- If the app backgrounds and Android later recreates or kills it, the app may still cold-start on the library route in this pass.
- Reopening the same book after that cold restart should restore the freshest safe position we can persist before background/stop, not an older debounced position.

### App Closed From Android / Battery Optimization Kill

- If the app is removed from recent tasks or later killed after entering the background, the user may reopen into the library route.
- The important guarantee for this pass is progress durability, not route persistence.
- The latest safe reader position should already be flushed before the later cold start happens.

### Book Reopen Flicker

- Opening a book with saved mid-chapter progress must not visibly show top-of-chapter content before the restore target appears.
- Fresh opens with no meaningful saved offset should keep the current fast path.
- TOC jumps, explicit chapter jumps, and sequential next/prev navigation must keep their existing visual behavior unless tests prove they need the same masking.

## Approaches Considered

### Option A: Persist the reader route and auto-return directly into the book after cold restart

Pros:

- strongest continuity story
- users return directly to the reader after process death

Cons:

- changes app-shell behavior, not just reader durability
- expands scope into route persistence and startup policy
- needs explicit product approval because it changes restart UX

Recommendation: reject for this pass.

### Option B: Lower the debounce and keep the rest of the design unchanged

Pros:

- very small implementation

Cons:

- still races lifecycle stop/task removal
- increases write frequency without guaranteeing last-position durability
- does nothing for the flicker

Recommendation: reject for this pass.

### Option C: Reader-local lifecycle flush plus reader-local restore reveal gate

Pros:

- solves both confirmed findings at the reader seam
- leaves `SettingsManager` schema and app-shell architecture intact
- keeps behavior changes narrow and testable

Cons:

- requires careful handling around `isInitialScrollDone`, `isRestoringPosition`, and the restore settle window
- needs new test coverage because current tests mostly verify final state, not lifecycle timing or first presentation

Recommendation: use this option.

## Approved Approach

Use one phased reader stabilization track:

### Phase 1: Progress Durability

Add a reader-local lifecycle flush that writes progress immediately when the active reader lifecycle moves toward background/stop.

The flush must:

- reuse `SettingsManager` as the persisted source of truth
- prefer the current visible list position when the reader state is stable
- fall back to the last known good persisted/restored progress when the reader is still restoring and the live list position is not trustworthy
- avoid writing bogus `(0, 0)` values during initial restore

This phase must not change DataStore keys, `BookProgress` shape, or app-shell route persistence.

### Phase 2: Open Restore Presentation

Hide the reader chapter runtime during the narrow initial restore window for saved mid-chapter reopens, then reveal it only after the restore target has landed and settled.

This phase must:

- keep the current chapter-selection logic
- keep the current restore mechanics and settle timing intact
- target only the cold-open saved-position path, not every chapter transition
- avoid masking TOC jumps and explicit navigation unless they actually share the same visible-jump problem

## Architecture Shape

Because `ReaderFeatureShell.kt` is already near the repo's file-size limit, this work should extract small helpers rather than adding another large block to that file.

Recommended file shape:

- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderProgressPersistence.kt`
  - pure helpers that build/fallback lifecycle progress snapshots
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderLifecycleProgressEffect.kt`
  - lifecycle observer composable that triggers a flush through the shell
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderRestorePresentation.kt`
  - pure rule(s) that decide whether initial content reveal should remain masked
- lightweight `ReaderFeatureShell.kt` edits to thread state into those helpers

`EpubReaderRuntime.kt` should only receive a narrow boolean contract such as "block chapter reveal until initial restore is ready." It should not own reader restore policy.

## Important Policy Decision For This Pass

Cold restart returning to the library route is acceptable in this pass.

The plan intentionally does **not** persist the active `AppRoute` or auto-reenter the reader after process death. That would be a larger product decision and builder policy change. This design focuses only on:

- making sure progress survives
- making sure book reopen does not flicker

## Testing Strategy

### New Coverage Needed

- a pure helper test for lifecycle progress snapshot selection and fallback
- a reader instrumentation test for background/recreate progress durability
- a reader instrumentation test that distinguishes first visible presentation from final restored state

### Existing Coverage To Keep Green

- `com.epubreader.feature.reader.ReaderScreenRestorationTest`
- `com.epubreader.feature.reader.ReaderScreenOverscrollTest`
- `com.epubreader.feature.reader.ReaderScreenThemeReactivityTest`
- `com.epubreader.feature.reader.ReaderChromeTapBehaviorTest`
- `com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest`
- `com.epubreader.feature.reader.ReaderSurfacePluginUnavailableTest`
- `com.epubreader.data.settings.SettingsManagerProgressPersistenceTest`

### Manual Verification Matrix

After implementation:

1. open a book, scroll, background the app briefly, reopen, confirm same live reader state
2. open a book, scroll, background the app, force-stop it, reopen app, reopen book, confirm restored progress is the latest safe position
3. open a book, scroll, dismiss the app from recent tasks, relaunch, reopen the book, confirm restored progress survives
4. reopen a book with saved mid-chapter progress and confirm the wrong top-of-chapter content is never visibly presented before the target position appears
5. rerun TOC jump, next/prev, and overscroll to confirm no reader navigation regressions

## Risks And Guards

- The biggest risk is saving unstable list coordinates during restore and overwriting a correct saved position.
- The second biggest risk is bloating `ReaderFeatureShell.kt` past the repo's size boundary instead of extracting helpers.
- The third risk is masking too many chapter transitions and turning a narrow flicker fix into a broader reader-loading UX change.

Guards:

- do not change `isInitialScrollDone`
- do not change `isRestoringPosition`
- do not remove the `delay(100)` restore settle contract
- do not rename `SettingsManager` keys
- do not move restore behavior into `:app`

## Non-Goals

- no startup-route persistence
- no "resume directly into the last book after process death" feature
- no changes to parser caching or book identity
- no selection, lookup-sheet, or system-bar changes in this pass

