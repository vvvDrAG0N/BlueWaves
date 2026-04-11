# System Overview

Blue Waves is a native Android EPUB reader focused on fast local reading, clear library management, and accurate reading-position restoration.

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

`com.epubreader.app.AppNavigation`
- Owns current screen selection.
- Manages library-level transient state such as selected books, drawer state, folder selection, and drag/drop preview state.
- Connects the library UI to `SettingsManager` and `EpubParser`.

### Data Layer

`com.epubreader.data.settings.SettingsManager`
- Persists global settings, folder state, and book progress in DataStore.

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
