# Blue Waves Agent Guide

This document defines the architecture, coding standards, and modification safety rules for the Blue Waves EPUB Reader project.

## 1. Core Philosophy
- **Native & High Performance**: Use Jetpack Compose and native Android APIs. Avoid heavy abstractions.
- **Privacy & Speed**: Offline-first. Cache metadata to avoid re-parsing large EPUBs.
- **Reading Focus**: The UI must remain clean and content-focused.

## 2. Context Priority
1. **AGENT.md**: Defines the behavior rules and constraints for all modifications.
2. **docs/** folder: Contains detailed system knowledge and flow documentation.
3. **Existing Code**: Always takes precedence over assumptions. Analyze current implementations before suggesting changes.
4. **Refactors**: Large-scale refactors or architectural shifts require explicit user confirmation.

## 3. AI Development Protocol
1. **Analyze Docs First**: Always review the `/docs` folder before writing any code. It contains the logic behind complex features.
2. **Minimal Edits**: Prefer surgical, minimal edits over large-scale rewrites to preserve original intent and code style.
3. **Protect the Reader**: Reader smoothness and scroll position restoration are sacred. Any changes to `ReaderScreen` must be stress-tested for these two properties.
4. **Confirm Changes**: Always ask for confirmation before making significant architectural changes or altering the DataStore schema.
5. **Verify Flows**: After any modification, re-verify the affected flow (e.g., if changing the parser, verify both metadata extraction and chapter rendering).

## 4. Architecture Rules

### Navigation
- **State-Based Navigation**: Use an `enum class Screen` in `MainActivity.kt` to switch views via `AnimatedContent`. 
- **DO NOT** add Jetpack Navigation Component unless complexity drastically increases.

### Data & Persistence
- **SettingsManager**: All global and per-book state must go through `SettingsManager.kt` using **Jetpack DataStore (Preferences)**.
- **Reactive Flow**: UI should collect settings as `State` to ensure real-time updates (e.g., font size changes reflecting instantly in the reader).

### EPUB Engine (`EpubParser.kt`)
- **Extraction Strategy**: EPUBs are extracted into `cacheDir/books/{id}`. 
- **Metadata Caching**: `metadata.json` is stored in the book's folder. Always check for this file before re-parsing the ZIP.
- **Sealed Element Hierarchy**: Use `ChapterElement` (Text/Image) to represent content. This keeps the `LazyColumn` logic clean.

## 4. Reader Logic & UI Conventions

### Forced LTR
- The project enforces `LayoutDirection.Ltr` in `MainActivity` and `ReaderScreen` to maintain consistent reading behavior (scrolling and gestures) even on RTL-configured devices.

### Theme Handling
- Use `getThemeColors(theme)` in `ReaderScreen.kt`. 
- Standard Material 3 color schemes are used for the Library/Settings, but the **Reader** uses specific hex colors (Sepia: `#F4ECD8`, Dark: `#121212`) for better eye comfort.

### Scroll & Progress Restoration
- The most critical logic is the sync between `LaunchedEffect` and `LazyListState` in `ReaderScreen.kt`.
- **Restoration Flow**: Wait for `totalItemsCount` to match `chapterElements.size` before attempting `scrollToItem`.
- **Position Saving**: Debounced saving (500ms delay) to prevent excessive disk writes during scrolling.

## 5. Implicit Coding Conventions
- **IO Safety**: Always use `withContext(Dispatchers.IO)` for parsing, file operations, and DataStore edits.
- **HTML Cleanup**: Use the `unescapeHtml()` and `normalizePath()` helpers in `EpubParser` to handle malformed XHTML and relative image paths.
- **Coroutines**: Prefer `rememberCoroutineScope()` for UI-triggered actions (like button clicks) and `LaunchedEffect` for state-driven side effects.

## 6. Modification Safety Rules (CRITICAL)
1. **Parser Changes**: When modifying `parseChapter`, ensure that the `ZipFile` stream is always closed and that image path normalization supports various EPUB internal structures (`OEBPS/`, `OPS/`, etc.).
2. **Reader Scroll**: Do not modify the `isInitialScrollDone` or `isRestoringPosition` flags without fully testing book opening and chapter switching. Breaking these will cause the reader to lose the user's place.
3. **Overscroll Navigation**: The `NestedScrollConnection` in `ReaderScreen` is sensitive. Ensure `verticalOverscroll` resets correctly on `Release`.
4. **Version Tracking**: If adding features that change the data schema, update `changelog.json` and handle version migration in `AppNavigation`.
- `Simulate the Validation Checklist: Before finalizing any change to ReaderScreen.kt, mentally execute the Validation Checklist found in ai_mental_model.md (Position Persistence, Chapter Boundary, Theme Reactivity, Parser Stress, Memory Safety). If the proposed change would cause any checklist item to fail, the change is unsafe and must be revised.`


## 7. Library Management
- The library is a scan of the `books` directory.
- `EpubBook.id` is a MD5 hash of URI + FileSize to prevent duplicate entries for the same file.
- Cover thumbnails are extracted once and stored as `cover_thumb.png` in the cache folder.

## AI Documentation Awareness
- `docs/` contains authoritative architectural memory.
- `ai_mental_model.md` defines validation expectations and safety heuristics.
- `quick_ref.md` is optimized for fast context loading and high-signal reference.
- **Mandatory Reading**: Agents must read these files before modifying `ReaderScreen.kt`, `EpubParser.kt`, or any core scroll and parsing logic.

## Documentation Preservation Rule
AI agents must treat AGENT.md and docs/ as stable architectural memory.
Do not refactor or summarize these files unless explicitly instructed.
New knowledge must be appended, never replaced.


