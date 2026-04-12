# System Overview

Blue Waves is a native Android EPUB reader focused on fast local reading, clear library management, and accurate reading-position restoration.

## Graph-First Entry

For repo-wide or cross-package work, start with:
1. `docs/project_graph.md`
2. `graphify-out/GRAPH_REPORT.md`
3. `graphify-out/wiki/index.md` if you need graph browsing

Only after that should you load area docs or raw source files.

## Tech Stack

- UI: Jetpack Compose
- Language: Kotlin
- Persistence: Jetpack DataStore (Preferences)
- EPUB engine: `epublib-core` plus custom `XmlPullParser` logic
- Image loading: Coil
- Concurrency: Coroutines and Flow

## Runtime Layers

### Entry Layer

`com.epubreader.MainActivity`
- Starts the app.
- Selects the app color scheme.
- Hosts the `Screen` enum used by state-based navigation.

### App Shell

`com.epubreader.app.AppNavigationContracts`
- Defines app-shell private constants and state/action contracts.
- Not a runtime owner, but the fastest way for an agent to understand app-shell surfaces.

`com.epubreader.app.AppNavigation`
- Owns current screen selection.
- Manages library-level transient state such as selected books, drawer state, folder selection, and drag/drop preview state.
- Connects the library UI to `SettingsManager` and `EpubParser`.

`com.epubreader.app.AppNavigationStartup`
- Computes first-run, version, and changelog decisions for the shell.

`com.epubreader.app.AppNavigationOperations`
- Defines package-local side-effect helpers used by the shell.
- Delegates writes to `SettingsManager` and file work to `EpubParser`.

`com.epubreader.app.AppNavigationLibraryData`
- Defines package-local pure helpers for folder derivation, sorting, and drag preview behavior.

`com.epubreader.app.AppNavigationLibrary`
- Renders drawer, top app bar, library grid, and selection action bar.
- Consumes already-derived state from `AppNavigation`.

`com.epubreader.app.AppNavigationDialogs`
- Renders sort sheet plus library-level confirm/info dialogs.
- Consumes visibility flags and callbacks from `AppNavigation`.

### Data Layer

`com.epubreader.data.settings.SettingsManager`
- Persists global settings, folder state, and book progress in DataStore.

`com.epubreader.data.settings.SettingsManagerContracts`
- Defines settings-layer private keys, defaults, and mapping helpers.
- Not a runtime owner, but the fastest way for an agent to understand persistence surfaces.

`com.epubreader.data.settings.SettingsManagerJson`
- Defines JSON helpers used by folder/order/group persistence paths.
- Consumed by `SettingsManager` only.

`com.epubreader.data.parser.EpubParser`
- Imports EPUB files into cache storage.
- Rebuilds and reads `metadata.json`.
- Parses chapters into renderable `ChapterElement` models.

### Feature Layer

`com.epubreader.feature.reader.ReaderScreen`
- Loads chapters.
- Restores scroll position.
- Saves reading progress.
- Handles overscroll-based chapter navigation and TOC interactions.

`com.epubreader.feature.settings.SettingsScreen`
- Edits global reader preferences and writes them back through `SettingsManager`.

### Shared Contracts

`com.epubreader.core.model.*`
- Shared models consumed by parser, persistence, and UI.

`com.epubreader.core.debug.AppLog`
- Shared lightweight logging wrapper for high-signal diagnostics.
- Debug/info logs are intended for debug builds only.

`com.epubreader.core.ui.*`
- Reusable presentational components for the library.

## Data Storage

- Cache: `cacheDir/books/{id}/`
- EPUB binary: `book.epub` inside each book folder
- Metadata: `metadata.json` inside each book folder
- Preferences: single DataStore file named `epub_settings`

## High-Risk Zones

- `feature/reader/ReaderScreen.kt`
  - timing-sensitive restoration and overscroll logic
- `data/parser/EpubParser.kt`
  - ZIP entry handling, malformed EPUB resilience, and image path normalization
- `data/settings/SettingsManager.kt`
  - folder/order persistence and progress integrity

## AI Context Hint

For app-shell tasks, do not load every app-shell file by default:

- Load `AppNavigationContracts.kt` first if you only need the shell dependency map.
- Load `AppNavigation.kt` first for behavior.
- Load `AppNavigationStartup.kt` only for first-run/version/changelog work.
- Load `AppNavigationOperations.kt` only for import/delete/last-read side effects.
- Load `AppNavigationLibraryData.kt` only for folder-order/sort/drag derivation work.
- Load `AppNavigationLibrary.kt` only for library rendering work.
- Load `AppNavigationDialogs.kt` only for modal/sheet work.

For settings persistence tasks:

- Load `SettingsManagerContracts.kt` first for the key/default map.
- Load `SettingsManager.kt` for behavior.
- Load `SettingsManagerJson.kt` only for folder metadata JSON behavior.

For EPUB parsing tasks:

- Load `docs/epub_parsing.md` first for the parser boundary map.
- Load `EpubParser.kt` for the public parser surface.
- Load `EpubParserBooks.kt` only for import/cache/metadata work.
- Load `EpubParserChapter.kt` only for chapter/image parsing work.

For reader tasks:

- Load `docs/reader_screen.md` first for the reader boundary map.
- Load `ReaderScreen.kt` for behavior, restoration, and progress flow.
- Load `ReaderScreenContracts.kt` if you only need reader theme or chrome contract context.
- Load `ReaderScreenChrome.kt` only for TOC, overlay, or app-bar work.
- Load `ReaderScreenControls.kt` only for controls, scrubber, theme, or chapter element rendering work.
