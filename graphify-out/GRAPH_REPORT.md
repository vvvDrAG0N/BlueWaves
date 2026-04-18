# Graph Report - C:\Users\Amon\Desktop\projects\Epub_Reader_v2  (2026-04-18)

## Corpus Check
- 36 files · ~81,458 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 464 nodes · 431 edges · 37 communities detected
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_PdfToEpubConverter Cluster|PdfToEpubConverter Cluster]]
- [[_COMMUNITY_EpubParser Cluster|EpubParser Cluster]]
- [[_COMMUNITY_EpubParserEditing Cluster|EpubParserEditing Cluster]]
- [[_COMMUNITY_EditBookScreen Cluster|EditBookScreen Cluster]]
- [[_COMMUNITY_app_src_main_java_com_epubreader_data_parser_epubparserbooks_kt Cluster|app_src_main_java_com_epubreader_data_parser_epubparserbooks_kt Cluster]]
- [[_COMMUNITY_SettingsManager Cluster|SettingsManager Cluster]]
- [[_COMMUNITY_ReaderScreenControls Cluster|ReaderScreenControls Cluster]]
- [[_COMMUNITY_EditBookModels Cluster|EditBookModels Cluster]]
- [[_COMMUNITY_SettingsScreen Cluster|SettingsScreen Cluster]]
- [[_COMMUNITY_SettingsModels Cluster|SettingsModels Cluster]]
- [[_COMMUNITY_ImportRouting Cluster|ImportRouting Cluster]]
- [[_COMMUNITY_AppNavigationOperations Cluster|AppNavigationOperations Cluster]]
- [[_COMMUNITY_AppNavigationContracts Cluster|AppNavigationContracts Cluster]]
- [[_COMMUNITY_LibraryModels Cluster|LibraryModels Cluster]]
- [[_COMMUNITY_app_src_main_java_com_epubreader_data_settings_settingsmanagerjson_kt Cluster|app_src_main_java_com_epubreader_data_settings_settingsmanagerjson_kt Cluster]]
- [[_COMMUNITY_BookEditingModels Cluster|BookEditingModels Cluster]]
- [[_COMMUNITY_AppNavigationDialogs Cluster|AppNavigationDialogs Cluster]]
- [[_COMMUNITY_EpubParserChapter Cluster|EpubParserChapter Cluster]]
- [[_COMMUNITY_PdfLegacyBridge Cluster|PdfLegacyBridge Cluster]]
- [[_COMMUNITY_MainActivity Cluster|MainActivity Cluster]]
- [[_COMMUNITY_SettingsManagerContracts Cluster|SettingsManagerContracts Cluster]]
- [[_COMMUNITY_PdfConversionWorker Cluster|PdfConversionWorker Cluster]]
- [[_COMMUNITY_pdftoepubconverter_pdfconversionworkspacestate Cluster|pdftoepubconverter_pdfconversionworkspacestate Cluster]]
- [[_COMMUNITY_PdfReaderScreen Cluster|PdfReaderScreen Cluster]]
- [[_COMMUNITY_AppNavigationLibrary Cluster|AppNavigationLibrary Cluster]]
- [[_COMMUNITY_AppNavigationLibraryData Cluster|AppNavigationLibraryData Cluster]]
- [[_COMMUNITY_app_src_main_java_com_epubreader_app_appnavigationstartup_kt Cluster|app_src_main_java_com_epubreader_app_appnavigationstartup_kt Cluster]]
- [[_COMMUNITY_AppLog Cluster|AppLog Cluster]]
- [[_COMMUNITY_PdfLegacyRuntime Cluster|PdfLegacyRuntime Cluster]]
- [[_COMMUNITY_ReaderScreenChrome Cluster|ReaderScreenChrome Cluster]]
- [[_COMMUNITY_ReaderScreenContracts Cluster|ReaderScreenContracts Cluster]]
- [[_COMMUNITY_check_graph_staleness Cluster|check_graph_staleness Cluster]]
- [[_COMMUNITY_AppNavigationPdfLegacy Cluster|AppNavigationPdfLegacy Cluster]]
- [[_COMMUNITY_LibraryCards Cluster|LibraryCards Cluster]]
- [[_COMMUNITY_ReaderTextActions Cluster|ReaderTextActions Cluster]]
- [[_COMMUNITY_AppNavigation Cluster|AppNavigation Cluster]]
- [[_COMMUNITY_ReaderScreen Cluster|ReaderScreen Cluster]]

## God Nodes (most connected - your core abstractions)
1. `EpubParser` - 34 edges
2. `SettingsManager` - 21 edges
3. `PdfConversionWorkspaceState` - 8 edges
4. `AppLog` - 5 edges
5. `PdfLegacyBridge` - 5 edges
6. `PdfLegacyRuntime` - 5 edges
7. `PdfConversionWorker` - 4 edges
8. `Image` - 3 edges
9. `PdfDocumentHandle` - 3 edges
10. `resolve_graphify_python()` - 3 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Communities

### Community 0 - "PdfToEpubConverter Cluster"
Cohesion: 0.05
Nodes (6): MlKitPdfToEpubConverter, PdfReflowSection, PdfTextExtractionMethod, PdfToEpubConverter, PdfWorkspacePage, WorkspacePageStat

### Community 1 - "EpubParser Cluster"
Cohesion: 0.06
Nodes (1): EpubParser

### Community 2 - "EpubParserEditing Cluster"
Cohesion: 0.08
Nodes (0): 

### Community 3 - "EditBookScreen Cluster"
Cohesion: 0.08
Nodes (3): ChapterDisplaySort, ChapterSelectionMode, EditBookTab

### Community 4 - "app_src_main_java_com_epubreader_data_parser_epubparserbooks_kt Cluster"
Cohesion: 0.09
Nodes (2): PdfDocumentInfo, ResolvedBookCoverFiles

### Community 5 - "SettingsManager Cluster"
Cohesion: 0.09
Nodes (1): SettingsManager

### Community 6 - "ReaderScreenControls Cluster"
Cohesion: 0.1
Nodes (2): NestedScrollWebView, ReaderControlsTab

### Community 7 - "EditBookModels Cluster"
Cohesion: 0.1
Nodes (2): EditableChapterItem, EditableChapterSource

### Community 8 - "SettingsScreen Cluster"
Cohesion: 0.1
Nodes (4): HsvColor, SettingsSection, ThemeEditorDraft, ThemeEditorSession

### Community 9 - "SettingsModels Cluster"
Cohesion: 0.11
Nodes (5): BookProgress, CustomTheme, GlobalSettings, ThemeOption, ThemePalette

### Community 10 - "ImportRouting Cluster"
Cohesion: 0.12
Nodes (9): ArchiveImportCandidate, ArchiveInspectionResult, Candidate, DirectEpubContainer, ImportFailureReason, ImportInspectionResult, ImportRequest, Ready (+1 more)

### Community 11 - "AppNavigationOperations Cluster"
Cohesion: 0.12
Nodes (6): Duplicate, EditBookResult, Failed, ImportBookResult, Imported, Updated

### Community 12 - "AppNavigationContracts Cluster"
Cohesion: 0.17
Nodes (10): BookSelectionActionBarActions, BookSelectionActionBarState, BookSelectionUiState, FolderDrawerActions, FolderDrawerUiState, LibraryAsyncUiState, LibraryDialogActions, LibraryDialogState (+2 more)

### Community 13 - "LibraryModels Cluster"
Cohesion: 0.17
Nodes (8): BookFormat, BookRepresentation, ChapterElement, ConversionStatus, EpubBook, Image, Text, TocItem

### Community 14 - "app_src_main_java_com_epubreader_data_settings_settingsmanagerjson_kt Cluster"
Cohesion: 0.17
Nodes (0): 

### Community 15 - "BookEditingModels Cluster"
Cohesion: 0.18
Nodes (10): BookChapterEdit, BookCoverAction, BookCoverUpdate, BookEditRequest, BookNewChapterContent, HtmlDocument, Keep, PlainText (+2 more)

### Community 16 - "AppNavigationDialogs Cluster"
Cohesion: 0.2
Nodes (0): 

### Community 17 - "EpubParserChapter Cluster"
Cohesion: 0.2
Nodes (0): 

### Community 18 - "PdfLegacyBridge Cluster"
Cohesion: 0.2
Nodes (4): PdfConversionProgress, PdfConversionProgressListener, PdfConversionResult, PdfLegacyBridge

### Community 19 - "MainActivity Cluster"
Cohesion: 0.22
Nodes (2): MainActivity, Screen

### Community 20 - "SettingsManagerContracts Cluster"
Cohesion: 0.22
Nodes (2): BookProgressPreferenceKeys, SettingsPreferenceKeys

### Community 21 - "PdfConversionWorker Cluster"
Cohesion: 0.25
Nodes (1): PdfConversionWorker

### Community 22 - "pdftoepubconverter_pdfconversionworkspacestate Cluster"
Cohesion: 0.25
Nodes (1): PdfConversionWorkspaceState

### Community 23 - "PdfReaderScreen Cluster"
Cohesion: 0.25
Nodes (1): PdfDocumentHandle

### Community 24 - "AppNavigationLibrary Cluster"
Cohesion: 0.29
Nodes (0): 

### Community 25 - "AppNavigationLibraryData Cluster"
Cohesion: 0.29
Nodes (1): FolderDragPreviewUpdate

### Community 26 - "app_src_main_java_com_epubreader_app_appnavigationstartup_kt Cluster"
Cohesion: 0.33
Nodes (1): AppShellStartupDecision

### Community 27 - "AppLog Cluster"
Cohesion: 0.33
Nodes (1): AppLog

### Community 28 - "PdfLegacyRuntime Cluster"
Cohesion: 0.33
Nodes (1): PdfLegacyRuntime

### Community 29 - "ReaderScreenChrome Cluster"
Cohesion: 0.33
Nodes (0): 

### Community 30 - "ReaderScreenContracts Cluster"
Cohesion: 0.33
Nodes (4): ReaderChromeCallbacks, ReaderChromeState, ReaderTheme, TocSort

### Community 31 - "check_graph_staleness Cluster"
Cohesion: 0.53
Nodes (5): check_staleness(), get_last_modified(), infer_python_from_graphify_bin(), interpreter_has_graphify(), resolve_graphify_python()

### Community 32 - "AppNavigationPdfLegacy Cluster"
Cohesion: 0.4
Nodes (0): 

### Community 33 - "LibraryCards Cluster"
Cohesion: 0.5
Nodes (0): 

### Community 34 - "ReaderTextActions Cluster"
Cohesion: 0.5
Nodes (3): Define, Translate, WebLookupAction

### Community 35 - "AppNavigation Cluster"
Cohesion: 1.0
Nodes (0): 

### Community 36 - "ReaderScreen Cluster"
Cohesion: 1.0
Nodes (0): 

## Knowledge Gaps
- **73 isolated node(s):** `Screen`, `LibraryAsyncUiState`, `LibraryScreenState`, `BookSelectionUiState`, `FolderDrawerUiState` (+68 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `AppNavigation Cluster`** (2 nodes): `AppNavigation()`, `AppNavigation.kt`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `ReaderScreen Cluster`** (2 nodes): `ReaderScreen.kt`, `ReaderScreen()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PdfConversionWorkspaceState` connect `pdftoepubconverter_pdfconversionworkspacestate Cluster` to `PdfToEpubConverter Cluster`?**
  _High betweenness centrality (0.003) - this node is a cross-community bridge._
- **What connects `Screen`, `LibraryAsyncUiState`, `LibraryScreenState` to the rest of the system?**
  _73 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `PdfToEpubConverter Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `EpubParser Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.06 - nodes in this community are weakly interconnected._
- **Should `EpubParserEditing Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._
- **Should `EditBookScreen Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._
- **Should `app_src_main_java_com_epubreader_data_parser_epubparserbooks_kt Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._