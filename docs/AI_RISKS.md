# AI Risk Registry: Blue Waves EPUB Reader

This document serves as a critical safety manual for AI agents modifying the Blue Waves codebase. It identifies "load-bearing" logic where small changes can cause catastrophic regressions in user experience or data integrity.

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
