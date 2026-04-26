# Test Checklist

Use this file to choose the smallest useful verification set. It is a coverage map, not a rollout history.

## Baseline Commands

- `.\gradlew.bat checkKotlinFileLineLimit verifyTestChecklistReferences`
- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat testDebugUnitTest`
- filtered instrumentation when the change is runtime/UI-specific

## High-Risk Instrumentation Suites

- Reader runtime:
  - `com.epubreader.feature.reader.ReaderScreenRestorationTest`
  - `com.epubreader.feature.reader.ReaderScreenOverscrollTest`
  - `com.epubreader.feature.reader.ReaderScreenThemeReactivityTest`
  - `com.epubreader.feature.reader.ReaderChromeTapBehaviorTest`
  - `com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest`
  - `com.epubreader.feature.reader.ReaderChapterSelectionHandleLayoutTest`
  - `com.epubreader.feature.reader.ReaderChapterSelectionContentReplacementTest`
  - `com.epubreader.feature.reader.ReaderSelectableTextStructureTest`
  - `com.epubreader.feature.reader.ReaderSurfacePluginUnavailableTest`
- App builder and feature flows:
  - `com.epubreader.app.AppNavigationLibraryFlowTest`
  - `com.epubreader.app.AppNavigationEditBookFlowTest`
  - `com.epubreader.app.AppNavigationPdfRepresentationFlowTest`
- Settings runtime:
  - `com.epubreader.feature.settings.SettingsScreenPersistenceTest`
  - `com.epubreader.feature.settings.SettingsScreenThemeEditorPersistenceTest`
  - `com.epubreader.feature.settings.SettingsScreenThemeGalleryPersistenceTest`
  - `com.epubreader.feature.settings.SettingsScreenSectionPersistenceTest`
  - `com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest`
  - `com.epubreader.feature.settings.SettingsThemeEditorModeInferenceTest`
  - `com.epubreader.feature.settings.SettingsThemePreviewSpecimenTest`
- Edit-book rendered boundary:
  - `com.epubreader.feature.editbook.EditBookSurfacePluginUnavailableTest`
- Parser integration:
  - `com.epubreader.data.parser.ParserIntegrationTest`

## Baseline JVM / Local Coverage Map

- Builder routing and shell seams:
  - `SurfaceRegistryTest`
  - `AppSurfaceRegistryTest`
  - `AppRouteTest`
  - `AppNavigationStartupTest`
- Library feature derivation and plugin boundary:
  - `LibrarySurfacePluginTest`
  - `LibraryFeatureDataTest`
  - `LibraryFeatureExtensionsTest`
  - `LibraryFeatureOperationsTest`
- Settings and themes:
  - `SettingsSurfacePluginTest`
  - `SettingsManagerContractsTest`
  - `SettingsManagerJsonTest`
  - `SettingsManagerProgressPersistenceTest`
  - `SettingsManagerThemePersistenceTest`
  - `SettingsModelsThemeTest`
  - `ThemeEditorGuidedColorEditTest`
  - `ThemeEditorModeInferenceTest`
- Parser and metadata helpers:
  - `EpubParserBooksTest`
  - `EpubParserChapterTest`
  - `EpubParserEditingTest`
  - `EpubParserImportTest`
  - `EpubParserLookupTest`
  - `EpubParserPdfFallbackTest`
  - `EpubParserScanCacheTest`
  - `ImportRoutingTest`
  - `PdfToEpubConverterTest`
- Reader and plugin helpers:
  - `ReaderSurfacePluginTest`
  - `ReaderExtensionHostTest`
  - `ReaderScreenContractsTest`
  - `ReaderScreenPrefetchTest`
  - `ReaderChapterSectionsTest`
  - `ReaderSelectionDocumentTest`
  - `ReaderSelectionGeometryTest`
  - `ReaderSelectionSessionRulesTest`
  - `ReaderSelectionStateTest`
- Edit-book and parked-PDF plugins:
  - `EditBookModelsTest`
  - `EditBookProgressRepairTest`
  - `EditBookSurfacePluginTest`
  - `PdfLegacyLegoPluginTest`

## Selection Rules

- Parser import/cache/edit change:
  - run parser JVM/local tests plus `ParserIntegrationTest`.
- Reader restoration, overscroll, theme, or selection change:
  - run reader-focused tests plus the relevant reader runtime instrumentation suites, including the split selection-host classes when the change touches selection behavior.
- Settings or DataStore shape change:
  - run settings JVM tests plus the relevant Settings persistence instrumentation family (`SettingsScreenPersistenceTest`, `SettingsScreenThemeEditorPersistenceTest`, `SettingsScreenThemeGalleryPersistenceTest`, and `SettingsScreenSectionPersistenceTest`).
- Builder route or feature-plugin boundary change:
  - run builder JVM/local tests plus the relevant surface plugin tests and unavailable-state instrumentation when the boundary owns loading or failure UI.
- Library, folder, or edit-book flow change:
  - run library/edit-book JVM coverage plus the relevant app-shell instrumentation flow tests.

## Manual Acceptance

- Import an EPUB, open it, reopen it, and confirm restoration.
- Use TOC jump, next/prev, and overscroll navigation.
- Change settings and themes, then confirm persistence.
- Select text, dismiss selection, open reader controls, and confirm define/translate still behave.
- Edit a book, save it, reopen it, and confirm metadata/chapter integrity.
- Tap a PDF-origin entry and confirm the shell still blocks active PDF behavior.
