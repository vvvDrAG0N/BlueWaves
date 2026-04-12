# Test Checklist

This file tracks the baseline automated coverage for Blue Waves after the app-shell, parser, settings, and reader safe refactors.

Use it to see what is already covered before planning new tests.

## Current State

- Plain JVM tests are supported and in use.
- Robolectric-style local Android tests are configured and in use.
- Instrumentation tests are in use on device/emulator for the highest-risk runtime behavior.
- The baseline 15-test execution plan below has been completed.

## Priority 1: JVM Unit Tests

### 1. [done] `AppNavigationLibraryDataTest`

- Path:
  - `app/src/test/java/com/epubreader/app/AppNavigationLibraryDataTest.kt`
- Target:
  - `app/AppNavigationLibraryData.kt`
- Cover:
  - `parseFolderOrder()` returns an empty list for malformed JSON
  - `buildFolders()` includes inferred folders, ordered folders, and `My Library`
  - `buildLibraryItems()` sorts by `added`, `title`, `author`, `recent`, and `chapters`
  - `updateFolderDragPreview()` reorders only when thresholds are crossed
  - `updateFolderDragPreview()` does not move folders above `My Library`
- Why first:
  - Pure logic
  - High regression value
  - No Android runtime needed

### 2. [done] `SettingsManagerJsonTest`

- Path:
  - `app/src/test/java/com/epubreader/data/settings/SettingsManagerJsonTest.kt`
- Target:
  - `data/settings/SettingsManagerJson.kt`
- Cover:
  - malformed JSON falls back safely
  - create folder updates persistence shape correctly
  - rename folder moves metadata without losing group/sort/order data
  - delete folder removes metadata and preserves unrelated folders
- Why second:
  - Central persistence helper logic
  - Good protection against startup crashes and folder-state corruption

### 3. [done] `EpubParserChapterTest`

- Path:
  - `app/src/test/java/com/epubreader/data/parser/EpubParserChapterTest.kt`
- Target:
  - `data/parser/EpubParserChapter.kt`
- Cover:
  - `normalizePath("OEBPS/images/../ch1.html") == "OEBPS/ch1.html"`
  - nested `../` segments resolve correctly
  - already-normalized paths stay unchanged
  - leading `../` segments do not crash
- Why third:
  - High-value parser safety
  - Pure helper, easy to test

### 4. [done] `EpubParserBooksTest`

- Path:
  - `app/src/test/java/com/epubreader/data/parser/EpubParserBooksTest.kt`
- Target:
  - `data/parser/EpubParserBooks.kt`
- Cover:
  - `saveBookMetadata()` and `loadBookMetadata()` round-trip valid metadata
  - missing optional `coverPath` is handled safely
  - missing optional `spineHrefs` is handled safely
  - malformed `metadata.json` returns `null`
- Why fourth:
  - Protects cached-library integrity
  - Still plain JVM if temp files are used

### 5. [done] `SettingsManagerContractsTest`

- Path:
  - `app/src/test/java/com/epubreader/data/settings/SettingsManagerContractsTest.kt`
- Target:
  - `data/settings/SettingsManagerContracts.kt`
- Cover:
  - default `GlobalSettings` mapping
  - default `BookProgress` mapping
  - missing preference keys produce stable defaults
- Why fifth:
  - Guards DataStore default behavior
  - Cheap to maintain

### 6. [done] `ReaderScreenContractsTest`

- Path:
  - `app/src/test/java/com/epubreader/feature/reader/ReaderScreenContractsTest.kt`
- Target:
  - `feature/reader/ReaderScreenContracts.kt`
- Cover:
  - `getThemeColors()` returns the expected theme colors
  - unknown theme falls back to light
- Why sixth:
  - Low-risk, low-cost coverage
  - Not as important as app-shell/settings/parser helpers

## Priority 2: Robolectric Tests

These local Android-aware tests are now configured and cover startup, app-shell operations, and parser facade behavior without needing a full device run.

### 7. [done] `AppNavigationStartupTest`

- Path:
  - `app/src/test/java/com/epubreader/app/AppNavigationStartupTest.kt`
- Target:
  - `app/AppNavigationStartup.kt`
- Cover:
  - first launch with no library shows welcome note
  - existing library clears `firstTime`
  - version bump selects changelog entries correctly
  - no matching changelog marks version as seen
- Why:
  - Good local validation for startup/version behavior once Android `Context` is available

### 8. [done] `AppNavigationOperationsTest`

- Path:
  - `app/src/test/java/com/epubreader/app/AppNavigationOperationsTest.kt`
- Target:
  - `app/AppNavigationOperations.kt`
- Cover:
  - duplicate import detection by `uri + file size`
  - imported book is assigned to root vs selected folder correctly
  - last-read touch updates timestamp without changing identity
- Why:
  - Important orchestration behavior
  - Easier with Android-aware local tests

### 9. [done] `EpubParserFacadeTest`

- Path:
  - `app/src/test/java/com/epubreader/data/parser/EpubParserFacadeTest.kt`
- Target:
  - `data/parser/EpubParser.kt`
- Cover:
  - import into cache folder
  - scan after cached metadata exists
  - duplicate folder lookup behavior
- Why:
  - Higher integration value than helper tests
  - Needs Android `Context` and filesystem setup

## Priority 3: Instrumentation / Compose Tests

These are slower and should focus on the most fragile runtime behavior.

### 10. [done] `ReaderScreenRestorationTest`

- Path:
  - `app/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenRestorationTest.kt`
- Target:
  - `feature/reader/ReaderScreen.kt`
- Cover:
  - reopen restores the saved chapter
  - reopen restores scroll position after content load
- Why first:
  - Highest-risk regression in the app

### 11. [done] `ReaderScreenOverscrollTest`

- Path:
  - `app/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenOverscrollTest.kt`
- Target:
  - `feature/reader/ReaderScreen.kt`
- Cover:
  - overscroll release goes to next chapter at bottom
  - overscroll release goes to previous chapter at top
  - thresholds do not trigger accidental chapter flips

### 12. [done] `ReaderScreenThemeReactivityTest`

- Path:
  - `app/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenThemeReactivityTest.kt`
- Target:
  - `feature/reader/ReaderScreen.kt`
- Cover:
  - changing theme updates reader colors without reopening

### 13. [done] `AppNavigationLibraryFlowTest`

- Path:
  - `app/src/androidTest/java/com/epubreader/app/AppNavigationLibraryFlowTest.kt`
- Target:
  - `app/AppNavigation.kt`
  - `app/AppNavigationLibrary.kt`
- Cover:
  - create folder flow
  - rename folder flow
  - delete folder flow
  - multi-select and move-books flow

### 14. [done] `SettingsScreenPersistenceTest`

- Path:
  - `app/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`
- Target:
  - `feature/settings/SettingsScreen.kt`
- Cover:
  - changing controls writes expected settings
  - values survive recomposition / screen reopen

### 15. [done] `ParserIntegrationTest`

- Path:
  - `app/src/androidTest/java/com/epubreader/data/parser/ParserIntegrationTest.kt`
- Target:
  - parser package
- Cover:
  - EPUB with `OEBPS/` layout
  - EPUB with `OPS/` layout
  - chapter with nested image paths like `../Images/...`

## Execution Pattern

Use the same order for future additions:

1. Start with pure JVM helper coverage.
2. Add Robolectric only when Android-aware local coverage buys real speed.
3. Use instrumentation for reader/runtime/device behavior that local tests cannot prove.

## Verification Commands

- JVM unit tests:
  - `.\gradlew.bat testDebugUnitTest`
- Single instrumentation class:
  - `.\gradlew.bat --% -Pandroid.testInstrumentationRunnerArguments.class=<fqcn> connectedDebugAndroidTest`
- Instrumentation tests:
  - `.\gradlew.bat connectedDebugAndroidTest`
- If production code changes while enabling testability:
  - `.\gradlew.bat assembleDebug`
