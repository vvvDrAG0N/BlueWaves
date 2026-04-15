# Test Checklist

This file tracks the baseline automated coverage for Blue Waves after the app-shell, parser, settings, reader, and theme-system work.

Use it to see what is already covered before planning new tests.

Temporary product note:
- Active app-shell PDF support is currently disabled.
- Parser-level PDF tests remain as parked internal coverage under `data/pdf/legacy`.
- App-shell PDF representation tests that exercised the old reader-switch flow are intentionally ignored until the safe refactor revives that surface.

## Current State

- Plain JVM tests are supported and in use.
- Robolectric-style local Android tests are configured and in use.
- Instrumentation tests are in use on device/emulator for the highest-risk runtime behavior.
- The baseline execution plan below has been completed and extended with custom-theme coverage.

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

### 4a. [done] `PdfToEpubConverterTest`

- Path:
  - `app/src/test/java/com/epubreader/data/parser/PdfToEpubConverterTest.kt`
- Target:
  - `data/pdf/legacy/PdfToEpubConverter.kt`
- Cover:
  - workspace pages are regrouped into bounded reflow sections
  - generated EPUB output writes section-based spine and NCX entries
- Why:
  - Protects the parked PDF reflow artifact shape without reopening PDF in the shell

### 4b. [done] `AppNavigationEditProgressTest`

- Path:
  - `app/src/test/java/com/epubreader/app/AppNavigationEditProgressTest.kt`
- Target:
  - `app/AppNavigationOperations.kt`
- Cover:
  - deleted saved chapter falls forward to the next surviving chapter
  - deleted last chapter falls back to the previous surviving chapter
  - unchanged saved href keeps the existing progress payload
  - unknown saved href falls back to the first valid chapter
- Why:
  - Protects safe restore behavior after chapter deletion in the Edit Book flow

### 4c. [done] `EditBookModelsTest`

- Path:
  - `app/src/test/java/com/epubreader/feature/editbook/EditBookModelsTest.kt`
- Target:
  - `feature/editbook/EditBookModels.kt`
- Cover:
  - chapter range selection picks inclusive bounds
  - outside-range selection inverts the chosen window
  - move-to-position keeps selected chapter order stable
  - imported html/xhtml title inference prefers document metadata over filenames
- Why:
  - Protects the dense Edit Book v2 chapter-list tools without needing Compose runtime coverage

### 5. [done] `SettingsManagerContractsTest`

- Path:
  - `app/src/test/java/com/epubreader/data/settings/SettingsManagerContractsTest.kt`
- Target:
  - `data/settings/SettingsManagerContracts.kt`
- Cover:
  - default `GlobalSettings` mapping
  - default `BookProgress` mapping
  - missing preference keys produce stable defaults
  - custom themes parse safely from Preferences
  - invalid stored active theme IDs fall back safely
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
  - saved custom themes resolve to explicit reader colors
- Why sixth:
  - Low-risk, low-cost coverage
  - Not as important as app-shell/settings/parser helpers

### 6a. [done] `SettingsModelsThemeTest`

- Path:
  - `app/src/test/java/com/epubreader/core/model/SettingsModelsThemeTest.kt`
- Target:
  - `core/model/SettingsModels.kt`
- Cover:
  - built-in theme options stay ahead of custom themes
  - active theme selection normalizes built-in, custom, and invalid IDs
  - custom palette hex parsing/formatting stays stable

### 6b. [done] `MainActivityThemeTest`

- Path:
  - `app/src/test/java/com/epubreader/MainActivityThemeTest.kt`
- Target:
  - `MainActivity.kt`
- Cover:
  - built-in sepia Material colors remain stable
  - saved custom themes derive an app-wide Material color scheme

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

### 9a. [done] `SettingsManagerThemePersistenceTest`

- Path:
  - `app/src/test/java/com/epubreader/data/settings/SettingsManagerThemePersistenceTest.kt`
- Target:
  - `data/settings/SettingsManager.kt`
- Cover:
  - saving and activating a custom theme persists through the public SettingsManager API
  - deleting the active custom theme falls back safely to the built-in light theme

### 9b. [done] `EpubParserEditingTest`

- Path:
  - `app/src/test/java/com/epubreader/data/parser/EpubParserEditingTest.kt`
- Target:
  - `data/parser/EpubParserEditing.kt`
- Cover:
  - metadata edits rewrite the stored EPUB safely
  - custom cover replacement and removal persist through `rebuildBookMetadata()`
  - chapter reorder plus html/xhtml import updates the spine/TOC without corrupting the archive
  - TOC-only rename does not rewrite existing chapter body content
  - deleting every chapter without replacement is rejected and leaves the source EPUB intact
- Why:
  - Gives focused coverage for the new EPUB mutation path behind Edit Book

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
  - switching to a saved custom theme updates reader colors without reopening

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
  - creating a custom theme auto-selects it and preserves it across screen reopen

### 15. [done] `ParserIntegrationTest`

- Path:
  - `app/src/androidTest/java/com/epubreader/data/parser/ParserIntegrationTest.kt`
- Target:
  - parser package
- Cover:
  - EPUB with `OEBPS/` layout
  - EPUB with `OPS/` layout
  - chapter with nested image paths like `../Images/...`

### 16. [done] `AppNavigationPdfRepresentationFlowTest`

- Path:
  - `app/src/androidTest/java/com/epubreader/app/AppNavigationPdfRepresentationFlowTest.kt`
- Target:
  - `app/AppNavigation.kt`
- Cover:
  - legacy PDF library entries show the temporary deprecation message instead of opening a reader surface
  - older representation-switch tests are parked with `@Ignore` until the planned PDF-safe-refactor redefines that flow

### 17. [done] `AppNavigationEditBookFlowTest`

- Path:
  - `app/src/androidTest/java/com/epubreader/app/AppNavigationEditBookFlowTest.kt`
- Target:
  - `app/AppNavigation.kt`
  - `feature/editbook/EditBookScreen.kt`
  - `data/parser/EpubParser.kt`
- Cover:
  - open Edit Book from the library card
  - update title/author in the details tab
  - switch to the chapters tab and add a text chapter
  - save returns to the library and refreshed metadata reflects the edit

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
