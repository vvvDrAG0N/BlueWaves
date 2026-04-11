# Package Map

This file documents the Phase 1 structural split intended to reduce context load for future AI agents.

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

## Data Layer

- `com.epubreader.data.settings.SettingsManager`
  - DataStore-backed source of truth for persisted app state.

- `com.epubreader.data.parser.EpubParser`
  - EPUB extraction, metadata caching, and chapter parsing.

## Feature UI

- `com.epubreader.feature.reader.ReaderScreen`
  - Reader UI and scroll restoration logic.
  - Treat as high risk. Read `ai_mental_model.md` before changing scroll or chapter restoration.

- `com.epubreader.feature.settings.SettingsScreen`
  - Settings UI for global reader preferences.

## Refactor Guardrails

- Keep state ownership aligned with docs: persistence in `SettingsManager`, transient UI in composables.
- Avoid moving shared models back into large feature files.
- Reader behavior changes should happen in a later phase, with validation against the reader checklist.
