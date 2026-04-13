# Architecture Overview

Blue Waves uses a reactive single-activity architecture with manual dependency passing and a small set of clearly separated layers.

## Graph-First Traversal

Use `docs/project_graph.md` plus `graphify-out/GRAPH_REPORT.md` before loading large groups of files.

Recommended order for low-token work:
1. `docs/project_graph.md`
2. `graphify-out/GRAPH_REPORT.md`
3. `graphify query "<question>" --budget 1200` if scope is still unclear
4. the single area doc for the target surface
5. only the raw files named by the graph and the area doc

## Structural Summary

### 1. App Entry (`MainActivity.kt`)

- Lives in `com.epubreader`.
- Owns app bootstrap, edge-to-edge setup, theme selection, and the `Screen` enum.
- Derives the app-level Material `ColorScheme` from the active built-in theme or saved custom theme.
- Intentionally small so future edits to navigation logic do not accumulate here.

### 2. App Shell (`app/AppNavigation.kt`)

- Lives in `com.epubreader.app`.
- Owns top-level navigation state and library-level transient UI state.
- Coordinates folder management, selection mode, dialogs, file import, and screen transitions.
- Instantiates and passes `SettingsManager` and `EpubParser` dependencies manually.

### 3. Shared Models (`core/model`)

- `LibraryModels.kt`: `EpubBook`, `TocItem`, `ChapterElement`
- `SettingsModels.kt`: `GlobalSettings`, `BookProgress`
- `SettingsModels.kt`: `GlobalSettings`, `BookProgress`, and shared theme palette contracts

These are the shared contracts between persistence, parsing, and UI. They were extracted from large files to reduce cross-file coupling.

### 4. Data Layer (`data/*`)

- `data/settings/SettingsManager.kt`
  - DataStore-backed source of truth for global settings, folder state, and reading progress.
- `data/settings/SettingsManagerContracts.kt`
  - Package-private key/default map and `Preferences` to model mappers.
  - Also maps the additive custom-theme registry stored in DataStore.
- `data/settings/SettingsManagerJson.kt`
  - Package-private JSON helpers for folder metadata edits.
- `data/parser/EpubParser.kt`
  - EPUB extraction, metadata caching, chapter parsing, and cached book scanning.

### 5. Feature Layer (`feature/*`)

- `feature/reader/ReaderScreen.kt`
  - Reader lifecycle, chapter loading, restoration, overscroll navigation, controls, and TOC behavior.
- `feature/settings/SettingsScreen.kt`
  - Reader preference editing UI.

### 6. Shared UI (`core/ui`)

- `LibraryCards.kt`
  - `BookItem`
  - `RecentlyViewedStrip`

These remain presentation-oriented and should not become new state owners.

### 7. Shared Debug Infra (`core/debug`)

- `AppLog.kt`
  - Small wrapper over Android logging.
  - Keeps debug/info logs debug-build only and gives the app one obvious place for high-signal diagnostics.

## Architectural Rules

- Navigation remains state-based through the `Screen` enum.
- `SettingsManager` remains the persisted source of truth.
- `EpubParser` remains responsible for file system and ZIP interactions.
- `ReaderScreen` remains the highest-risk feature and should be modified conservatively.
- New non-trivial features should add the smallest appropriate automated test, or explicitly document why automated coverage is being skipped.

## Why This Layout Is AI-Friendlier

- Smaller files reduce the amount of context an agent must load before making a safe change.
- Shared models now have one obvious home.
- Entry, data, feature, and shared UI responsibilities are easier to infer from package names.
- App bootstrapping and app navigation are no longer mixed into the same file.

## App Shell Detail

The app shell is now split across six files with different roles:

- `app/AppNavigationContracts.kt`
  - App-shell private contract map.
  - Shared constants plus bundled state/action types for library UI and dialogs.

- `app/AppNavigation.kt`
  - Shell state owner.
  - Startup/version/changelog coordinator.
  - Screen router for `Library`, `Reader`, and `Settings`.

- `app/AppNavigationStartup.kt`
  - Startup/version/changelog decision helpers.
  - Computes what the shell should show without owning writes itself.

- `app/AppNavigationOperations.kt`
  - App-shell side-effect helpers.
  - Import, scan, last-read update, and destructive mutation coordination.

- `app/AppNavigationLibraryData.kt`
  - Pure library derivation helpers.
  - JSON parsing, folder ordering, sort filtering, and drag-preview updates.

- `app/AppNavigationLibrary.kt`
  - Presentational library shell.
  - Drawer, top bar, grid, and bottom action bar.

- `app/AppNavigationDialogs.kt`
  - Presentational modal surfaces.
  - Sort sheet, folder dialogs, bulk delete dialogs, and welcome/changelog dialogs.

This split is meant to reduce per-task context load rather than total LOC. An agent should usually load `AppNavigationContracts.kt` plus `AppNavigation.kt`, then open only the focused helper or rendering file needed by the task.

## Settings Layer Detail

The settings layer is now split across three files with different roles:

- `data/settings/SettingsManagerContracts.kt`
  - Key/default map.
  - `Preferences` to `GlobalSettings` and `BookProgress` mapping helpers.

- `data/settings/SettingsManager.kt`
  - Public persistence API.
  - DataStore edit transactions and persistence behavior.

- `data/settings/SettingsManagerJson.kt`
  - JSON helpers for folder sorts, folder order, and book groups.

This split is meant to reduce per-task context load without changing the DataStore schema. An agent should usually load `SettingsManagerContracts.kt` plus `SettingsManager.kt`, and only open `SettingsManagerJson.kt` if folder metadata behavior is relevant.

## Parser Layer Detail

The parser layer is now split across three files with different roles:

- `data/parser/EpubParser.kt`
  - Public parser facade.
  - Used by `AppNavigation` and `ReaderScreen`.

- `data/parser/EpubParserBooks.kt`
  - Book rebuild and metadata cache logic.
  - Keeps book ID generation, TOC reconstruction, cover extraction, and `metadata.json` persistence together.

- `data/parser/EpubParserChapter.kt`
  - Chapter parsing logic.
  - Keeps `ZipFile` handling, malformed XHTML cleanup, image lookup, and `normalizePath()` together.

This split is meant to reduce per-task context load without changing parser behavior. An agent should usually load `docs/epub_parsing.md` plus `EpubParser.kt`, then open only the focused helper file needed by the task.

## Reader Layer Detail

The reader layer is now split across four files with different roles:

- `feature/reader/ReaderScreen.kt`
  - Reader state owner.
  - Keeps chapter loading, restoration, save-progress, and navigation effects together.

- `feature/reader/ReaderScreenContracts.kt`
  - Reader contract map.
  - Shared theme helpers plus bundled state/callback contracts for reader chrome.

- `feature/reader/ReaderScreenChrome.kt`
  - Reader shell and overlays.
  - TOC drawer, top bar, overscroll prompts, and scroll-to-top FAB.

- `feature/reader/ReaderScreenControls.kt`
  - Reader controls and rendering helpers.
  - Bottom settings controls, scrubber UI, theme buttons, and chapter element rendering.

This split is meant to reduce per-task context load without changing the restoration state machine. An agent should usually load `docs/reader_screen.md` plus `ReaderScreen.kt`, then open only the focused helper file needed by the task.
