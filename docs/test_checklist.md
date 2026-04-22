# Test Checklist

Use this file to choose the smallest useful verification set. It is a coverage map, not a rollout history.

## Baseline Commands

- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat testDebugUnitTest`
- filtered instrumentation when the change is runtime/UI-specific

## High-Risk Instrumentation Suites

- Reader runtime:
  - `com.epubreader.feature.reader.ReaderScreenRestorationTest`
  - `com.epubreader.feature.reader.ReaderScreenOverscrollTest`
  - `com.epubreader.feature.reader.ReaderScreenThemeReactivityTest`
- App shell flows:
  - `com.epubreader.app.AppNavigationLibraryFlowTest`
  - `com.epubreader.app.AppNavigationEditBookFlowTest`
- Settings runtime:
  - `com.epubreader.feature.settings.SettingsScreenPersistenceTest`
- Parser integration:
  - `com.epubreader.data.parser.ParserIntegrationTest`

## Baseline JVM / Local Coverage Map

- App shell and folder derivation:
  - `AppNavigationLibraryDataTest`
  - `AppNavigationStartupTest`
  - `AppNavigationOperationsTest`
- Settings and themes:
  - `SettingsManagerContractsTest`
  - `SettingsManagerJsonTest`
  - `SettingsManagerThemePersistenceTest`
  - `SettingsModelsThemeTest`
- Parser and metadata helpers:
  - `EpubParserBooksTest`
  - `EpubParserChapterTest`
  - `EpubParserFacadeTest`
- Reader/theme helpers:
  - `ReaderScreenContractsTest`
- Edit-book models:
  - `EditBookModelsTest`

## Selection Rules

- Parser import/cache/edit change:
  - run parser JVM/local tests plus `ParserIntegrationTest`.
- Reader restoration, overscroll, theme, or selection change:
  - run reader-focused tests and the three reader instrumentation suites.
- Settings or DataStore shape change:
  - run settings JVM tests plus `SettingsScreenPersistenceTest`.
- Library, folder, or edit-book shell flow change:
  - run app-shell JVM/local tests plus the relevant app-shell instrumentation flow tests.

## Manual Acceptance

- Import an EPUB, open it, reopen it, and confirm restoration.
- Use TOC jump, next/prev, and overscroll navigation.
- Change settings and themes, then confirm persistence.
- Edit a book, save it, reopen it, and confirm metadata/chapter integrity.
- Tap a PDF-origin entry and confirm the shell still blocks active PDF behavior.
