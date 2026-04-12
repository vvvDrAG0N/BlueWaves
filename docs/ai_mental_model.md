# AI Mental Model: Engineering Reasoning for Blue Waves

This document provides a heuristic framework for AI agents to reason about, modify, and validate the Blue Waves EPUB Reader codebase.

Path note after Phase 1 refactor:
- `ReaderScreen.kt` now lives in `feature/reader/`
- `EpubParser.kt` now lives in `data/parser/`
- `SettingsManager.kt` now lives in `data/settings/`
- Top-level library and navigation orchestration now lives in `app/AppNavigation.kt`

## 1. Reasoning About State Changes

### The State Lifecycle
1.  **Persistence (Truth)**: `SettingsManager` (Jetpack DataStore) is the source of truth for all user preferences and reading progress.
2.  **Collection**: `MainActivity` and `ReaderScreen` collect `Flows` from `SettingsManager` into Compose `State`.
3.  **UI State**: Local UI state (e.g., `currentChapterIndex`, `showControls`) drives the UI.
4.  **Feedback Loop**: UI interactions trigger `scope.launch { settingsManager.update... }`, which updates the DataStore, causing a new value to flow back to the UI.

### Key Heuristic
When adding or modifying a feature, ask: **"Does this need to survive a process death?"**
- If YES: Add a key to `SettingsManager` and drive the UI via its `Flow`.
- If NO: Use `rememberSaveable` or standard `mutableStateOf`.

## 2. Fragile Core Components

### The "Scroll Sync" (ReaderScreen.kt)
The synchronization between `chapterElements` loading and `LazyListState.scrollToItem` is the most fragile part of the app.
- **Dependency**: `isInitialScrollDone` and `isRestoringPosition` prevent the "Save Progress" logic from overwriting the correct position with `(0, 0)` during the loading phase.
- **Fragility**: Modifying the `LaunchedEffect` that handles restoration can easily lead to "Jump to Top" bugs where users lose their place upon opening a book.

### EPUB Stream Handling (EpubParser.kt)
- **ZipFile Leaks**: All `ZipFile` and `InputStream` operations MUST be wrapped in `.use {}` or explicitly closed.
- **Parser Resiliency**: EPUBs are often malformed. The `XmlPullParser` loop must be wrapped in try-catches to prevent single-chapter crashes from killing the entire app.

## 3. Common Failure Modes

1.  **Infinite Scroll Updates**: Updating DataStore inside a LaunchedEffect that observes that same DataStore value without a debounce or guard flag.

❌ Anti-pattern: `LaunchedEffect(settings) { settingsManager.updateGlobalSettings(settings) }`
This creates an infinite loop: settings change -> effect runs -> updates settings -> triggers recomposition -> effect runs again.

✅ Correct pattern: Use `LaunchedEffect(Unit)` for one-time reads, or use `snapshotFlow` with `distinctUntilChanged()` and a debounce when reacting to user-driven state changes.

2.  **IO on Main Thread**: EPUB parsing or image decoding occurring outside of `Dispatchers.IO`.
3.  **Context Mismanagement**: Using `LocalContext` inside a `LaunchedEffect` without considering that the context might change or the effect might outlive the composition.
4.  **Path Normalization**: Failing to handle relative paths in `img src` tags (e.g., `../Images/fig1.jpg`), resulting in broken images.

## 4. Validation Checklist for Edits

- [ ] **Position Persistence**: Open a book, scroll to a random spot, exit the app, and re-enter. Does it land on the same pixel?
- [ ] **Chapter Boundary**: Use the overscroll gesture to change chapters. Does the new chapter start at the correct end (top if next, bottom if previous)?
- [ ] **Theme Reactivity**: Change the theme in `SettingsScreen`. Does the `ReaderScreen` background and text color update immediately without a reload?
- [ ] **Parser Stress**: Test with an EPUB that has nested directories (e.g., `OEBPS/Text/ch1.xhtml`).
- [ ] **Memory Safety**: Ensure `ChapterElement.Image` byte arrays are not held in memory longer than necessary (check `remember` blocks).

## 5. Safe Modification Strategy

1.  **Analyze Side Effects**: Before modifying `ReaderScreen`, trace the `isInitialScrollDone` flag to see how it gates other effects.
2.  **IO-First**: When adding new parser logic, implement the core extraction in `EpubParser` first, ensuring it works in isolation.
3.  **UI Isolation**: Keep rendering logic inside `ChapterElement` handlers simple. Complex text styling should be handled via `AnnotatedString` helpers rather than deep nested Composables.
4.  **Incremental Persistence**: If adding a new setting, add the key to `SettingsManager` first, then the UI control, then the reactive implementation.

## 6. Change Impact Map

- If you modify `EpubParser.parseChapter()`, you MUST test EPUBs with nested image paths (`../Images/`, `OEBPS/`, `OPS/`) and verify memory usage does not spike, because image path resolution is fragile and byte arrays can leak.
- If you modify any `LaunchedEffect` inside `ReaderScreen.kt`, you MUST test the full lifecycle: open a book, scroll to a random position, kill the app process, and reopen. Verify the exact pixel scroll position is restored. Failure here indicates a "Jump to Top" regression.
- If you modify `SettingsManager` preference keys or schema, you MUST test an upgrade from the previous app version. Ensure DataStore reads old keys without crashing and that `lastSeenVersionCode` migration logic handles missing values gracefully.
- If you modify the overscroll navigation logic (`NestedScrollConnection` or `pointerInput` in `ReaderScreen.kt`), you MUST test pull-to-change-chapter with both fast flicks and slow drags. Verify that `verticalOverscroll` resets correctly on `PointerEventType.Release` and that chapters do not skip.
- If you modify the flags `isInitialScrollDone` or `isRestoringPosition`, you MUST run the "Jump to Top" regression test: open a book, wait for content to load, ensure scroll position is not (0,0) unless it's a fresh chapter. These flags gate the progress-saving logic and are the single highest-risk area of the codebase.

## Parser Split Note

The parser package now uses a facade-plus-helpers layout:

- `EpubParser.kt` is the public surface.
- `EpubParserBooks.kt` owns book ID generation, TOC/cover rebuild, and `metadata.json` persistence.
- `EpubParserChapter.kt` owns the `ZipFile` chapter loop, image resolution, malformed XHTML cleanup, and `normalizePath()`.

Safe loading strategy:
- Read `docs/epub_parsing.md` first.
- Open `EpubParser.kt`.
- Open only the helper file relevant to the bug or refactor.

## Reader Split Note

The reader package now uses a state-owner plus helpers layout:

- `ReaderScreen.kt` is the behavior and restoration surface.
- `ReaderScreenContracts.kt` owns reader theme helpers and the chrome contract map.
- `ReaderScreenChrome.kt` owns the TOC drawer, overlays, and shell layout.
- `ReaderScreenControls.kt` owns controls, scrubber UI, and chapter element rendering.

Safe loading strategy:
- Read `docs/reader_screen.md` first.
- Open `ReaderScreen.kt`.
- Open only the helper file relevant to the bug or refactor.

