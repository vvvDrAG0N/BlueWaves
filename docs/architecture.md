# Architecture Overview

Blue Waves is a modular single-activity Android app with manual dependency passing, graph-first docs, and an EPUB-first active shell.

## Product Boundary

- The active shell imports and opens EPUB only.
- PDF-origin books may still appear in library metadata and scans.
- Active PDF open/import remains intentionally disabled until a future explicit refactor.

## Module Graph

- `:app`
  - `MainActivity` bootstrap and `AppNavigation` shell assembly.
- `:core:model`
  - Shared pure models, theme contracts, and edit-book request models.
- `:core:ui`
  - Shared Compose presentation helpers and reusable UI primitives.
- `:data:settings`
  - `SettingsManager`, key/default mapping, and JSON-backed folder metadata helpers.
- `:data:books`
  - `EpubParser`, metadata/chapter/editing helpers, and parser-side PDF legacy seams.
- `:feature:library`
  - Library rendering surfaces consumed by the app shell.
- `:feature:reader`
  - Reader state owner, chrome, controls, and text-selection UI.
- `:feature:settings`
  - Reader/settings surfaces and theme-management UI.
- `:feature:editbook`
  - EPUB metadata, cover, and chapter mutation UI.
- `:feature:pdf-legacy`
  - Parked legacy runtime kept outside the active shell.

Repo guard:
- `checkKotlinFileLineLimit` fails when Kotlin files under `src/main`, `src/test`, or `src/androidTest` exceed 500 lines.

## Runtime Layers

### Entry Layer

`MainActivity.kt`
- Owns bootstrap, edge-to-edge setup, app theme selection, and the `Screen` enum.
- Must stay small. Do not move app-shell state back into it.

### App Shell

`AppNavigationContracts.kt`
- Fastest shell contract map for state/action bundles.

`AppNavigation.kt`
- Owns top-level screen selection, library transient state, startup effects, and shell coordination.

`AppNavigationStartup.kt`
- Startup/version/changelog decision helpers.

`AppNavigationOperations.kt`
- Import, delete, last-read touch, edit-book save coordination, and other shell side effects.

`AppNavigationLibraryData.kt`
- Pure folder derivation, sort/filter, and drag-preview helpers.

`AppNavigationLibrary.kt`
- Drawer, top bar, library grid, and selection action bar rendering.

`AppNavigationDialogs.kt`
- Sort sheet plus library-level dialogs.

`AppNavigationPdfLegacy.kt`
- Small shell bridge for the intentionally disabled PDF boundary.

### Data Layer

`SettingsManagerContracts.kt`
- Key/default map and `Preferences` to model mappers.

`SettingsManager.kt`
- Public persistence API and all DataStore edit transactions.

`SettingsManagerJson.kt`
- Folder-order, folder-sort, and group JSON helpers.

`EpubParser.kt`
- Public parser facade used by the shell and reader.

`EpubParserBooks.kt`
- Book ID generation, metadata cache, cover extraction, and TOC rebuild.

`EpubParserEditing.kt`
- Staged EPUB mutation for the Edit Book flow.

`EpubParserChapter.kt`
- Chapter parsing, image lookup, malformed XHTML tolerance, and `normalizePath()`.

`PdfLegacyBridge.kt`
- Parser-side seam for parked PDF runtime work.

### Feature Layer

`feature/library/*`
- Presentational library components driven by shell-derived state.

`EditBookScreen.kt`
- EPUB-only editor for metadata, cover, and chapter mutation flows.

`ReaderScreen.kt`
- Reader state owner. Keeps chapter loading, restoration, save-progress, and navigation effects.

`ReaderScreenContracts.kt`
- Reader contract map plus theme/helper types.

`ReaderScreenChrome.kt`
- Drawer, top bar, overlays, and shell layout.

`ReaderScreenControls.kt`, `ReaderChapterContent.kt`, `ReaderControlsSections.kt`, `ReaderControlsWidgets.kt`, `ReaderVerticalScrubber.kt`
- Reader controls, chapter rendering, widgets, and scrubber support.

`SettingsScreen.kt` plus split settings files
- Global reader settings, theme management, import/export, and related UI.

`feature/pdf-legacy/*`
- Parked runtime only. Not part of the active shell flow.

### Shared Layer

`core/model/*`
- `EpubBook`, `TocItem`, `ChapterElement`, `GlobalSettings`, `BookProgress`, theme contracts, and edit-book models.

`core/ui/*`
- Shared presentation helpers such as library cards and reader UI support.

`core/debug/AppLog.kt`
- Shared logging surface for high-signal diagnostics.

## Ownership Rules

- `:app` assembles and routes. It must not become a feature-logic dumping ground.
- `:feature:*` owns feature-specific UI and orchestration.
- `:core:model` owns shared pure contracts and models.
- `:core:ui` owns shared presentation-only helpers.
- `:data:*` owns persistence, parsing, storage, and runtime behavior.
- Do not use feature-to-feature imports for convenience reuse. Extract shared code downward.

## Loading Hints

- Repo-wide routing -> `docs/project_graph.md`
- App shell work -> `docs/app_shell_navigation.md`
- Settings or progress persistence -> `docs/settings_persistence.md`
- Parser or EPUB mutation work -> `docs/epub_parsing.md`
- Reader work -> `docs/reader_screen.md`
- High-risk behavior changes -> `docs/ai_mental_model.md`
- Verification selection -> `docs/test_checklist.md`

## High-Risk Zones

- `feature/reader/ReaderScreen.kt`
  - Restoration timing, save-progress gating, and overscroll navigation.
- `data/settings/SettingsManager.kt`
  - DataStore keys/defaults, folder metadata, and progress integrity.
- `data/parser/EpubParserBooks.kt` / `EpubParserChapter.kt`
  - `buildBookId(...)`, `metadata.json`, ZIP safety, and `normalizePath()`.
- `data/parser/EpubParserEditing.kt`
  - Atomic EPUB rewrites for the Edit Book flow.
