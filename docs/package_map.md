# Package Map

This file documents the Phase 1 structural split intended to reduce context load for future AI agents.

Graph-first rule:
- Read `docs/project_graph.md` and, if present, `graphify-out/GRAPH_REPORT.md` before reading this whole file for broad tasks.
- Use `graphify query "<question>" --budget 1200` when you are not sure which package boundary matters.

## Entry Point

- `com.epubreader.MainActivity`
  - Owns app bootstrap, theme selection, and the `Screen` enum.
  - Keep this file small. Do not move navigation state back into it.

## App Shell

- `com.epubreader.app.AppNavigation`
  - Owns top-level navigation and library-level transient UI state.
  - Coordinates `SettingsManager`, `EpubParser`, `ReaderScreen`, and `SettingsScreen`.

## Core Models

- `com.epubreader.core.model.LibraryModels`
  - `EpubBook`
  - `TocItem`
  - `ChapterElement`

- `com.epubreader.core.model.SettingsModels`
  - `GlobalSettings`
  - `BookProgress`

These are shared contracts. Prefer evolving them here instead of reintroducing duplicate models in parser or settings files.

## Shared UI

- `com.epubreader.core.ui.LibraryCards`
  - `BookItem`
  - `RecentlyViewedStrip`

Keep these presentation-only. Do not move folder state or navigation side effects into this package.

## Shared Debug Infra

- `com.epubreader.core.debug.AppLog`
  - Lightweight app logging wrapper.
  - Use this for new high-signal logs instead of scattering raw `android.util.Log` usage.

## Data Layer

- `com.epubreader.data.settings.SettingsManager`
  - DataStore-backed source of truth for persisted app state.

- `com.epubreader.data.settings.SettingsManagerContracts`
  - Package-private key/default map plus `Preferences` to model mappers.

- `com.epubreader.data.settings.SettingsManagerJson`
  - Package-private JSON helpers for folder-order, folder-sort, and book-group persistence.

- `com.epubreader.data.parser.EpubParser`
  - EPUB extraction, metadata caching, and chapter parsing.

## Feature UI

- `com.epubreader.feature.reader.ReaderScreen`
  - Reader UI and scroll restoration logic.
  - Treat as high risk. Read `ai_mental_model.md` before changing scroll or chapter restoration.

- `com.epubreader.feature.settings.SettingsScreen`
  - Settings UI for global reader preferences.

## Reader Split

- `com.epubreader.feature.reader.ReaderScreen`
  - State owner and effect coordinator for chapter loading, restoration, progress saving, and navigation.
  - Start here for behavior questions.

- `com.epubreader.feature.reader.ReaderScreenContracts`
  - Reader contract map for theme helpers, `TocSort`, and chrome bundles.
  - Start here when you only need the reader dependency surface.

- `com.epubreader.feature.reader.ReaderScreenChrome`
  - TOC drawer, top bar, overscroll UI, and reader shell layout.
  - Load only for chrome or drawer rendering work.

- `com.epubreader.feature.reader.ReaderScreenControls`
  - Bottom controls, scrubber, theme buttons, and chapter element rendering.
  - Load only for controls, scrubber, theme, or text/image rendering work.

- AI loading guidance:
  - Do not load all reader files by default.
  - Start with `docs/reader_screen.md`, then `ReaderScreen.kt`.
  - Open `ReaderScreenChrome.kt` or `ReaderScreenControls.kt` only if the task is specific to that surface.

## Refactor Guardrails

- Keep state ownership aligned with docs: persistence in `SettingsManager`, transient UI in composables.
- Avoid moving shared models back into large feature files.
- Reader behavior changes should happen in a later phase, with validation against the reader checklist.

## Phase 2 App Shell Split

- `com.epubreader.app.AppNavigation`
  - Still owns app-shell state, startup/version effects, and action coordination.
  - This is still the first file to read for behavior questions.

- `com.epubreader.app.AppNavigationContracts`
  - Owns app-shell private constants plus bundled state/action contracts.
  - Read this first when you only need the dependency surface for library UI or dialogs.

- `com.epubreader.app.AppNavigationStartup`
  - Owns startup/version/changelog decision helpers.
  - Load only for first-run/version behavior work.

- `com.epubreader.app.AppNavigationOperations`
  - Owns app-shell side-effect helpers for import, scan, last-read updates, and destructive mutations.
  - Load only for side-effect behavior work.

- `com.epubreader.app.AppNavigationLibraryData`
  - Owns pure folder derivation, sorting, JSON parsing, and drag-preview helpers.
  - Load only for folder-order/sort/drag behavior work.

- `com.epubreader.app.AppNavigationLibrary`
  - Owns library drawer/top bar/grid/action-bar rendering only.
  - Prefer reading this file only for library presentation tasks.

- `com.epubreader.app.AppNavigationDialogs`
  - Owns sort sheet and library-level dialogs.
  - Prefer reading this file only for modal copy/layout/confirm-flow tasks.

- AI loading guidance:
  - Do not load all app-shell files by default.
  - Start with `AppNavigationContracts.kt` for the contract map or `AppNavigation.kt` for behavior.
  - Then load only the focused helper file needed by the task: `AppNavigationStartup.kt`, `AppNavigationOperations.kt`, `AppNavigationLibraryData.kt`, `AppNavigationLibrary.kt`, or `AppNavigationDialogs.kt`.

## Settings Persistence Split

- `com.epubreader.data.settings.SettingsManager`
  - Remains the only public persistence entry point.
  - Owns DataStore edit transactions and behavior.

- `com.epubreader.data.settings.SettingsManagerContracts`
  - Owns key/default definitions and model mapping helpers.
  - Start here when you need the persistence surface map.

- `com.epubreader.data.settings.SettingsManagerJson`
  - Owns JSON mutation helpers for folder metadata.
  - Load only for folder/order/group tasks.

## Parser Split

- `com.epubreader.data.parser.EpubParser`
  - Remains the only public parser facade.
  - Start here for the parser surface and behavior entry points.

- `com.epubreader.data.parser.EpubParserBooks`
  - Owns book rebuild, cover extraction, TOC reconstruction, metadata.json read/write, and book ID generation.
  - Load this file only for import/cache/metadata work.

- `com.epubreader.data.parser.EpubParserChapter`
  - Owns chapter XML parsing, ZIP entry lookup, image resolution, and `normalizePath()`.
  - Load this file only for chapter/image parsing work.

- AI loading guidance:
  - Do not load all parser files by default.
  - Start with `docs/epub_parsing.md`, then `EpubParser.kt`.
  - Open `EpubParserBooks.kt` or `EpubParserChapter.kt` only if the task specifically touches that path.
