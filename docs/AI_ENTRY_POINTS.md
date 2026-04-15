# AI Entry Points

This file maps major features to their current package locations, primary files, and the first functions worth tracing.

Graph-first note:
- For cross-package questions, start with `docs/project_graph.md`.
- If present, read `graphify-out/GRAPH_REPORT.md` before loading raw files.
- Use `graphify query "<question>" --budget 1200` when you need the graph to narrow the file set.

## Library

Entry point:
- `com.epubreader.app.AppNavigation` -> `AppNavigation()`

Core files:
- `data/settings/SettingsManagerContracts.kt`
- `app/AppNavigationContracts.kt`
- `app/AppNavigation.kt`
- `app/AppNavigationStartup.kt`
- `app/AppNavigationOperations.kt`
- `app/AppNavigationLibraryData.kt`
- `app/AppNavigationLibrary.kt`
- `app/AppNavigationDialogs.kt`
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
- Load `AppNavigationContracts.kt` first for the shell surface map.
- Then load `AppNavigation.kt` for behavior.
- Open `AppNavigationStartup.kt`, `AppNavigationOperations.kt`, or `AppNavigationLibraryData.kt` only if the task is specific to that helper path.
- Open `AppNavigationLibrary.kt` or `AppNavigationDialogs.kt` only if the task is rendering-specific.
- PDF import/open behavior is currently disabled at this layer; if a task mentions PDF, start here before touching the parked runtime files.

## Reader

Entry point:
- `com.epubreader.feature.reader.ReaderScreen` -> `ReaderScreen()`

Core files:
- `feature/reader/ReaderScreen.kt`
- `feature/reader/ReaderScreenContracts.kt`
- `feature/reader/ReaderScreenChrome.kt`
- `feature/reader/ReaderScreenControls.kt`
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
- Start with `docs/reader_screen.md`, then `ReaderScreen.kt`.
- Open `ReaderScreenChrome.kt` only for drawer/overlay work and `ReaderScreenControls.kt` only for controls/rendering work.
- The active reader path no longer carries PDF fallback UI; `PdfReaderScreen.kt` is deprecated runtime code and is not part of the active reader entry path right now.

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
- Deprecated PDF parsing/conversion internals now live under `data/pdf/legacy`, but the active app shell only imports/opens EPUBs for now.

## Deprecated PDF Runtime

Entry points:
- `com.epubreader.feature.pdf.legacy.PdfReaderScreen`
- `com.epubreader.data.pdf.legacy.PdfToEpubConverter`
- `com.epubreader.data.pdf.legacy.PdfConversionWorker`
- `com.epubreader.app.AppNavigationPdfLegacy`

Current status:
- Kept in source for the planned safe refactor.
- Not part of the active import/open path in `AppNavigation`.

## Parser Split Note

Load order for parser work:
- `docs/epub_parsing.md`
- `data/parser/EpubParser.kt`
- `data/parser/EpubParserBooks.kt` only for import, metadata, TOC, cover, or book ID work
- `data/parser/EpubParserChapter.kt` only for chapter XML, image resolution, or `normalizePath()` work

Parser function ownership:
- `parseAndExtract()` and `reparseBook()` still enter through `EpubParser`
- `buildBookId(...)`, metadata cache, and TOC rebuild now live in `EpubParserBooks.kt`
- `parseBookChapter(...)` and `normalizePath()` now live in `EpubParserChapter.kt`

## Progress Persistence

Entry point:
- `com.epubreader.data.settings.SettingsManager`

Core files:
- `data/settings/SettingsManagerContracts.kt`
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
- Load `SettingsManagerContracts.kt` first for keys/defaults and progress shape, then `SettingsManager.kt` for behavior.
