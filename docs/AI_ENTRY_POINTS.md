# AI Entry Points

This file maps major features to their current package locations, primary files, and the first functions worth tracing.

## Library

Entry point:
- `com.epubreader.app.AppNavigation` -> `AppNavigation()`

Core files:
- `app/AppNavigation.kt`
- `core/ui/LibraryCards.kt`
- `data/settings/SettingsManager.kt`
- `data/parser/EpubParser.kt`

Key functions:
- `refreshLibrary()`
- `updateBookGroup()`
- `updateFolderOrder()`
- `createFolder()`
- `renameFolder()`
- `deleteFolder()`
- `scanBooks()`
- `deleteBook()`

Data flow:
- `AppNavigation` -> `EpubParser.scanBooks()` -> `cache/books/*/metadata.json` -> derived folder/book UI

AI hint:
- Folder and ordering bugs usually start in persisted state or derived folder state, not the row composables.

## Reader

Entry point:
- `com.epubreader.feature.reader.ReaderScreen` -> `ReaderScreen()`

Core files:
- `feature/reader/ReaderScreen.kt`
- `data/parser/EpubParser.kt`
- `data/settings/SettingsManager.kt`
- `core/model/LibraryModels.kt`
- `core/model/SettingsModels.kt`

Key functions and areas:
- `LaunchedEffect(book.id)`
- `LaunchedEffect(currentChapterIndex)`
- `LaunchedEffect(chapterElements)`
- `saveBookProgress()`
- `parseChapter()`
- `getThemeColors()`

Data flow:
- `EpubParser.parseChapter()` -> `ReaderScreen` state -> `LazyColumn` -> `SettingsManager.saveBookProgress()`

AI hint:
- Treat the `chapterElements` restoration effect as load-bearing logic. Preserve sequencing.

## Settings

Entry point:
- `com.epubreader.feature.settings.SettingsScreen` -> `SettingsScreen()`

Core files:
- `feature/settings/SettingsScreen.kt`
- `data/settings/SettingsManager.kt`
- `core/model/SettingsModels.kt`

Key functions:
- `updateGlobalSettings()`
- `setFavoriteLibrary()`
- `setLibrarySort()`

Data flow:
- `SettingsScreen` -> `SettingsManager` -> `globalSettings` Flow -> `MainActivity` and `ReaderScreen`

AI hint:
- Theme and reader preference changes should remain purely DataStore-driven.

## EPUB Parsing

Entry point:
- `com.epubreader.data.parser.EpubParser`

Core files:
- `data/parser/EpubParser.kt`
- `core/model/LibraryModels.kt`

Key functions:
- `parseAndExtract()`
- `reparseBook()`
- `parseChapter()`
- `scanBooks()`
- `loadMetadata()`
- `updateLastRead()`

Data flow:
- `Uri` -> `book.epub` in `cache/books/{id}` -> `metadata.json` + parsed chapter elements

AI hint:
- Check `normalizePath()` and ZIP entry resolution first for broken image/chapter cases.

## Progress Persistence

Entry point:
- `com.epubreader.data.settings.SettingsManager`

Core files:
- `data/settings/SettingsManager.kt`
- `feature/reader/ReaderScreen.kt`
- `core/model/SettingsModels.kt`

Key functions:
- `getBookProgress()`
- `saveBookProgress()`
- `deleteBookData()`

Data flow:
- `ReaderScreen` scroll state -> `BookProgress` -> DataStore -> later restoration in `ReaderScreen`

AI hint:
- Progress correctness depends on stable `book.id` generation in `EpubParser`.
