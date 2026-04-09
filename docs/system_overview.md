# System Overview

Blue Waves is a native Android EPUB reader focused on a clean, vertical-scrolling reading experience.

## Tech Stack
- **UI**: Jetpack Compose (100%)
- **Language**: Kotlin
- **Persistence**: Jetpack DataStore (Preferences)
- **EPUB Engine**: `epublib-core` + Custom `XmlPullParser` logic
- **Image Loading**: Coil
- **Concurrency**: Kotlin Coroutines & Flow

## Core Components & Responsibilities

### `com.epubreader.MainActivity`
The entry point and navigation host. Manages the `Screen` state and handles top-level events like book selection and global theme toggling.

### `com.epubreader.EpubParser`
Handles all file system and ZIP operations. It extracts EPUB files, caches metadata as JSON, and parses XHTML chapters into a list of renderable `ChapterElement` objects.

### `com.epubreader.SettingsManager`
Wraps Jetpack DataStore. It provides a reactive API (`Flow`) for global settings (font, theme) and per-book reading progress (scroll position).

### `com.epubreader.ReaderScreen`
The most complex UI component. It manages the chapter lifecycle, implements overscroll-based navigation between chapters, and ensures the user's reading position is restored accurately.

### `com.epubreader.SettingsScreen`
Provides a user interface for modifying global reader preferences, which are persisted immediately via `SettingsManager`.

## Data Storage
- **Cache Directory**: Books are extracted to `cacheDir/books/{id}/`.
- **Metadata**: Each book has a `metadata.json` for fast library loading.
- **Preferences**: Global and progress data are stored in a single DataStore file `epub_settings`.
