# AI Entry Points

This file maps each major feature to its entry files and key functions to help AI agents navigate the codebase efficiently.

---

FEATURE: Library

ENTRY POINT:
MainActivity.kt → AppNavigation()

CORE FILES:
MainActivity.kt
SettingsManager.kt
EpubParser.kt (scanBooks, deleteBook)

KEY FUNCTIONS:
refreshLibrary()
deleteBook()
updateBookGroup()
scanBooks()

DATA FLOW:
UI (AppNavigation) → EpubParser.scanBooks() → cache/books/*/metadata.json → UI

AI_HINT:
Start debugging folder/book state in SettingsManager, not UI.

---

FEATURE: Reader

ENTRY POINT:
ReaderScreen.kt → ReaderScreen()

CORE FILES:
ReaderScreen.kt
EpubParser.kt
SettingsManager.kt

KEY FUNCTIONS:
loadChapter()
saveBookProgress()
scrollToItem()
parseChapter()

DATA FLOW:
EpubParser (extract/parse) → ReaderScreen (LazyColumn) → SettingsManager (save progress)

AI_HINT:
Scroll restoration logic is sensitive; verify `isInitialScrollDone` in ReaderScreen.

---

FEATURE: Settings

ENTRY POINT:
SettingsScreen.kt → SettingsScreen()

CORE FILES:
SettingsScreen.kt
SettingsManager.kt

KEY FUNCTIONS:
updateTheme()
setFontSize()
toggleAnimation()

DATA FLOW:
UI (SettingsScreen) → SettingsManager (DataStore) → Global Flow → UI (MainActivity/Reader)

AI_HINT:
Settings are persisted in DataStore via SettingsManager.

---

FEATURE: EPUB Parsing

ENTRY POINT:
EpubParser.kt

CORE FILES:
EpubParser.kt

KEY FUNCTIONS:
parseAndExtract()
parseChapter()
scanBooks()
generateMD5()

DATA FLOW:
Uri/File → ZipFile → cache/books/ → metadata.json / chapter_*.json

AI_HINT:
Check `normalizePath` for image loading issues in chapters.

---

FEATURE: Progress Persistence

ENTRY POINT:
SettingsManager.kt

CORE FILES:
SettingsManager.kt
ReaderScreen.kt

KEY FUNCTIONS:
saveBookProgress()
getBookProgress()
updateLastRead()

DATA FLOW:
ReaderScreen (scroll) → SettingsManager (DataStore) → MainActivity (Library sort)

AI_HINT:
Progress is saved by book ID. Verify ID consistency in EpubParser.
