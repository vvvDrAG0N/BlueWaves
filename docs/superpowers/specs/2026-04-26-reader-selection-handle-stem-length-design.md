# Reader Selection Handle Stem Length Design

## Goal

Reduce the effective stem length of both reader text-selection handles by 50% in the active Reader plugin while keeping the handles mirrored and preserving existing selection behavior.

## Scope

- Reader selectable-text handle geometry only
- Start and end handles equally
- EPUB reader selection flow only

Out of scope:

- Selection range logic
- Scroll restoration and reader progress behavior
- Action bar placement rules
- DataStore, parser, or navigation changes

## Current Ownership

- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
  - Derives the handle stem height passed into the selection handle layer.
  - This file is the implementation owner for the host behavior.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderInternalFacades.kt`
  - Provides the feature-local forwarding facade into the runtime host.
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderSelectionHandles.kt`
  - Builds handle dimensions and renders the visual handle.
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderSelectionGeometry.kt`
  - Resolves mirrored handle layout and exposes geometry used by rendering, semantics, and drag pickup.

## Approved Approach

Apply the 50% reduction at the Reader host source where the stem height is derived from the reader font size.

Instead of passing the full font-size-derived stem height into `ReaderSelectionHandleLayer`, the host will pass half that value. The existing handle layer and geometry pipeline will remain unchanged so the shorter stem height naturally flows through:

- rendered stem length
- semantics-reported geometry
- drag pickup point and effective handle layout

This keeps the behavior aligned with the request for full handle geometry reduction rather than a paint-only change.

## Why This Approach

- Smallest safe change surface
- Preserves current ownership boundaries
- Avoids adding new configuration or API surface for a fixed visual adjustment
- Lets the existing mirrored layout math continue to own start/end symmetry

## Testing Plan

Follow a red-green cycle with targeted reader tests:

1. Add or extend a focused reader test that activates selection and asserts a 50% stem reduction for a known `fontSize` by reading `ReaderSelectionHandleSemanticsData` and deriving the effective stem height from `stemBottomYInHandle - stemTopYInHandle`.
2. Run the targeted test first and confirm it fails for the expected reason.
3. Implement the source reduction in `ReaderChapterSelectionHost.kt`.
4. Re-run the targeted reader test and confirm it passes.
5. Run the focused JVM geometry test to confirm the generic handle layout behavior still remains internally consistent.

## Risks And Guards

- The handle files already have local uncommitted workspace changes, so implementation must integrate with current file contents instead of assuming a clean base.
- Reader restoration and progress-saving state must remain untouched.
- The existing minimum stem-height guard in `ReaderSelectionHandles.kt` may cap very small sizes; the verification step should confirm the final effective height still reflects the requested reduction for normal reader font sizes.
