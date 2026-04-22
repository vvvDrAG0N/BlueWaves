# AI Mental Model And Risk Register

Use this guide before changing reader restoration, parser core logic, DataStore-backed persistence, or EPUB mutation behavior.

## State Model

1. Persistence truth lives in `SettingsManager`.
2. `MainActivity` and `ReaderScreen` collect persisted state as Compose state.
3. Local UI state drives the current screen and transient interactions.
4. User actions write back through `SettingsManager` or `EpubParser`, then flow into the UI again.

Key question:
- Does this behavior need to survive process death?
  - If yes, it belongs in persisted state.
  - If no, keep it as local UI state.

## Validation Checklist

- Position persistence:
  - Open a book, move to a random spot, exit, reopen, and confirm the same reading position.
- Chapter boundary behavior:
  - Verify next/prev and overscroll land at the correct edge of the adjacent chapter.
- Theme reactivity:
  - Change the theme and confirm reader colors update without a reload.
- Parser stress:
  - Verify nested EPUB paths such as `OEBPS/` and `../Images/...`.
- Memory safety:
  - Avoid reintroducing long-lived in-memory image payloads or stale parser caches.

## Critical Invariants

### Reader Restoration

- `isInitialScrollDone` and `isRestoringPosition` gate the save-progress effect.
- `snapshotFlow` must wait for the rendered item count before `scrollToItem(...)`.
- The `delay(100)` settle step is load-bearing.

### Progress Overwrite Hazard

- During load or chapter transitions, the list may transiently report `(0, 0)`.
- Saving progress before restoration settles will overwrite the correct position.

### Overscroll Sensitivity

- Chapter transitions depend on the custom overscroll/release sequence.
- Reset behavior on release is part of the contract.

### Book Identity

- `buildBookId(...)` must remain `MD5(uri + fileSize)`.
- Changing that identity will orphan progress and cache folders.

### Parser Safety

- `ZipFile` and stream lifetimes must stay inside `.use {}`.
- `normalizePath()` must keep handling `../`, `OEBPS/`, and `OPS/`.
- Malformed XHTML must fail safely rather than crashing the whole import/read path.

### Persistence Safety

- DataStore key names and default meanings are compatibility boundaries.
- Folder metadata and custom themes are JSON-backed; parsing must stay resilient.

### Edit Book Safety

- EPUB writes must stay staged and atomic.
- TOC-only rename should not silently rewrite unrelated chapter bodies.
- Cover removal must clear stale cover artifacts.

### Layout Direction

- Forced `LayoutDirection.Ltr` is part of gesture correctness, not just styling.

## Safe Modification Strategy

- Trace the owner state before changing effects or callbacks.
- Keep file, ZIP, and DataStore work on `Dispatchers.IO`.
- Use the smallest focused helper file instead of loading whole packages.
- Promote stable architecture rules into canonical docs or `AGENTS.md`, not into transient memory notes.

## Change-Impact Map

- If you touch `ReaderScreen.kt` effects:
  - run the full reader validation checklist.
- If you touch `EpubParserBooks.kt` identity or cache logic:
  - verify scan, duplicate detection, and progress continuity.
- If you touch `EpubParserChapter.kt`:
  - verify broken-image and malformed-XHTML cases.
- If you touch `SettingsManager` keys/defaults:
  - verify compatibility with existing persisted data.
- If you touch `EpubParserEditing.kt`:
  - verify edit-save, reopen, TOC, cover, and chapter-order integrity.
