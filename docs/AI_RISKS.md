# AI Risk Registry: Blue Waves EPUB Reader

This document serves as a critical safety manual for AI agents modifying the Blue Waves codebase. It identifies "load-bearing" logic where small changes can cause catastrophic regressions in user experience or data integrity.

Path note after Phase 1 refactor:
- `ReaderScreen.kt` now lives in `feature/reader/`
- `EpubParser.kt` now lives in `data/parser/`
- `SettingsManager.kt` now lives in `data/settings/`
- `AppNavigation()` now lives in `app/AppNavigation.kt`

## 1. The "Jump to Top" Regression (ReaderScreen.kt)
**Area**: Scroll Restoration State Machine
**Risk Level**: CRITICAL
**Description**: The reader uses a non-standard, timing-sensitive sequence to restore the user's reading position across chapters.
**Sacred Flags**: 
- `isInitialScrollDone`: Gates the "Save Progress" effect. If set to `true` too early, it will save `(0,0)` over the user's actual progress.
- `isRestoringPosition`: Prevents the UI from flickering or triggering overscroll gestures during the jump.
**Constraint**: Any modification to `LaunchedEffect(chapterElements)` must preserve the `snapshotFlow` -> `scrollToItem` -> `delay(100)` -> `flag update` sequence.

## 2. Reading Progress Orphanage (EpubParser.kt)
**Area**: Book Identity Generation
**Risk Level**: CRITICAL
**Description**: Books are identified by an MD5 hash of `(URI + FileSize)`.
**Constraint**: This logic is IMMUTABLE. Changing the hashing algorithm or the input strings will change the `bookId`, causing the app to lose all reading progress, bookmarks, and cached covers associated with that file.

## 3. UI Mirroring & Layout Direction (Global)
**Area**: Theming and Layout
**Risk Level**: HIGH
**Description**: The app enforces `LayoutDirection.Ltr` globally.
**Constraint**: Do not remove `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`. The custom scrubbers and gesture-based chapter navigation are hardcoded for LTR coordinates. Removing this will break the UI in RTL locales (Arabic, Hebrew, etc.).

## 4. Theme Contrast Hazards (ReaderScreen.kt / SettingsManager.kt)
**Area**: Color Management
**Risk Level**: MEDIUM
**Description**: The app uses hardcoded hex values (e.g., Sepia `#F4ECD8`) instead of standard Material Surface tokens.
**Constraint**: When adding new UI components to the `ReaderScreen`, you must manually check the current theme string and apply the correct foreground/background colors from `getThemeColors()`. Standard Material `onSurface` tokens may not have sufficient contrast against these custom backgrounds.

## 5. JSON Deserialization Fragility (SettingsManager.kt)
**Area**: Persistence Layer
**Risk Level**: MEDIUM
**Description**: Folder structures and sorting preferences are stored as raw JSON strings within DataStore.
**Constraint**: There is no schema validation for these strings. Ensure all manual JSON manipulations (`JSONObject`, `JSONArray`) are wrapped in try-catch blocks and provide sensible defaults to prevent app-wide crashes on startup.

## 6. Parser Split Awareness (`data/parser/*`)
**Area**: EPUB Parsing Boundaries
**Risk Level**: MEDIUM
**Description**: The parser is now split across a public facade and two helper files.
**Constraint**: Start with `docs/epub_parsing.md` and `EpubParser.kt`, then load only the targeted helper file. Do not change `buildBookId(...)`, `normalizePath()`, or ZIP stream handling while working in `EpubParserBooks.kt` or `EpubParserChapter.kt`.

## 7. Reader Split Awareness (`feature/reader/*`)
**Area**: Reader Boundaries
**Risk Level**: HIGH
**Description**: The reader is now split across a state owner, a contract map, and two UI helper files.
**Constraint**: Start with `docs/reader_screen.md` and `ReaderScreen.kt`, then load only the targeted helper file. Do not move restoration sequencing, progress-save effects, or overscroll state ownership out of `ReaderScreen.kt` without explicit validation.
