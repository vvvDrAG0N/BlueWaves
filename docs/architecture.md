# Architecture Overview

Blue Waves is built with a modern, reactive, single-activity architecture using Jetpack Compose.

## Key Components

### 1. Navigation Controller (`MainActivity.kt`)
- Manages top-level state using a custom `Screen` enum (`Library`, `Reader`, `Settings`).
- Uses `AnimatedContent` for screen transitions.
- Handles system bar visibility and edge-to-edge configuration.
- Coordinates version-based changelog display on startup.

### 2. EPUB Engine (`EpubParser.kt`)
- **Extraction**: Unpacks EPUB files into the app's cache directory.
- **Parsing**: Uses `epublib` for initial structure and a custom `XmlPullParser` for chapter content.
- **Sealed Data Model**: Converts HTML into a list of `ChapterElement` (Text/Image) for efficient rendering in Compose.

### 3. Persistence Layer (`SettingsManager.kt`)
- **Technology**: Jetpack DataStore (Preferences).
- **Global Settings**: Stores UI preferences like font size, theme, and line height.
- **Progress Tracking**: Saves per-book reading position (chapter, scroll index, and offset) using book-specific keys.

### 4. UI Layer
- **Library**: `LazyVerticalGrid` showing book covers.
- **Reader**: Complex `LazyColumn` with position restoration logic.
- **Settings**: Direct interface to update DataStore values.

## Dependency Management
- **Manual DI**: Dependencies (`SettingsManager`, `EpubParser`) are instantiated in the Activity/Navigation level and passed down as parameters.
- **Reactive Streams**: UI components collect `Flow` from DataStore as `State` to react immediately to preference changes.
