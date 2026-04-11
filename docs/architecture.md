# Architecture Overview

Blue Waves uses a reactive single-activity architecture with manual dependency passing and a small set of clearly separated layers.

## Structural Summary

### 1. App Entry (`MainActivity.kt`)

- Lives in `com.epubreader`.
- Owns app bootstrap, edge-to-edge setup, theme selection, and the `Screen` enum.
- Intentionally small so future edits to navigation logic do not accumulate here.

### 2. App Shell (`app/AppNavigation.kt`)

- Lives in `com.epubreader.app`.
- Owns top-level navigation state and library-level transient UI state.
- Coordinates folder management, selection mode, dialogs, file import, and screen transitions.
- Instantiates and passes `SettingsManager` and `EpubParser` dependencies manually.

### 3. Shared Models (`core/model`)

- `LibraryModels.kt`: `EpubBook`, `TocItem`, `ChapterElement`
- `SettingsModels.kt`: `GlobalSettings`, `BookProgress`

These are the shared contracts between persistence, parsing, and UI. They were extracted from large files to reduce cross-file coupling.

### 4. Data Layer (`data/*`)

- `data/settings/SettingsManager.kt`
  - DataStore-backed source of truth for global settings, folder state, and reading progress.
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

## Architectural Rules

- Navigation remains state-based through the `Screen` enum.
- `SettingsManager` remains the persisted source of truth.
- `EpubParser` remains responsible for file system and ZIP interactions.
- `ReaderScreen` remains the highest-risk feature and should be modified conservatively.

## Why This Layout Is AI-Friendlier

- Smaller files reduce the amount of context an agent must load before making a safe change.
- Shared models now have one obvious home.
- Entry, data, feature, and shared UI responsibilities are easier to infer from package names.
- App bootstrapping and app navigation are no longer mixed into the same file.
