# Graph Report - C:\Users\Abdul\Desktop\projects\BlueWaves  (2026-04-14)

## Corpus Check
- 27 files · ~51,482 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 291 nodes · 265 edges · 27 communities detected
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_EpubParser Cluster|EpubParser Cluster]]
- [[_COMMUNITY_SettingsManager Cluster|SettingsManager Cluster]]
- [[_COMMUNITY_SettingsModels Cluster|SettingsModels Cluster]]
- [[_COMMUNITY_PdfToEpubConverter Cluster|PdfToEpubConverter Cluster]]
- [[_COMMUNITY_SettingsScreen Cluster|SettingsScreen Cluster]]
- [[_COMMUNITY_ImportRouting Cluster|ImportRouting Cluster]]
- [[_COMMUNITY_ReaderScreenControls Cluster|ReaderScreenControls Cluster]]
- [[_COMMUNITY_EpubParserBooks Cluster|EpubParserBooks Cluster]]
- [[_COMMUNITY_SettingsManagerJson Cluster|SettingsManagerJson Cluster]]
- [[_COMMUNITY_AppNavigationOperations Cluster|AppNavigationOperations Cluster]]
- [[_COMMUNITY_LibraryModels Cluster|LibraryModels Cluster]]
- [[_COMMUNITY_AppNavigationContracts Cluster|AppNavigationContracts Cluster]]
- [[_COMMUNITY_AppNavigationDialogs Cluster|AppNavigationDialogs Cluster]]
- [[_COMMUNITY_MainActivity Cluster|MainActivity Cluster]]
- [[_COMMUNITY_EpubParserChapter Cluster|EpubParserChapter Cluster]]
- [[_COMMUNITY_SettingsManagerContracts Cluster|SettingsManagerContracts Cluster]]
- [[_COMMUNITY_PdfReaderScreen Cluster|PdfReaderScreen Cluster]]
- [[_COMMUNITY_AppNavigationLibraryData Cluster|AppNavigationLibraryData Cluster]]
- [[_COMMUNITY_AppNavigationLibrary Cluster|AppNavigationLibrary Cluster]]
- [[_COMMUNITY_AppNavigationStartup Cluster|AppNavigationStartup Cluster]]
- [[_COMMUNITY_AppLog Cluster|AppLog Cluster]]
- [[_COMMUNITY_ReaderScreenChrome Cluster|ReaderScreenChrome Cluster]]
- [[_COMMUNITY_ReaderScreenContracts Cluster|ReaderScreenContracts Cluster]]
- [[_COMMUNITY_LibraryCards Cluster|LibraryCards Cluster]]
- [[_COMMUNITY_check_graph_staleness Cluster|check_graph_staleness Cluster]]
- [[_COMMUNITY_AppNavigation Cluster|AppNavigation Cluster]]
- [[_COMMUNITY_ReaderScreen Cluster|ReaderScreen Cluster]]

## God Nodes (most connected - your core abstractions)
1. `EpubParser` - 30 edges
2. `SettingsManager` - 21 edges
3. `AppLog` - 5 edges
4. `Image` - 3 edges
5. `PdfDocumentHandle` - 3 edges
6. `MainActivity` - 2 edges
7. `PdfToEpubConverter` - 2 edges
8. `MlKitPdfToEpubConverter` - 2 edges
9. `HsvColor` - 2 edges
10. `ThemeEditorDraft` - 2 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Communities

### Community 0 - "EpubParser Cluster"
Cohesion: 0.06
Nodes (1): EpubParser

### Community 1 - "SettingsManager Cluster"
Cohesion: 0.09
Nodes (1): SettingsManager

### Community 2 - "SettingsModels Cluster"
Cohesion: 0.11
Nodes (5): BookProgress, CustomTheme, GlobalSettings, ThemeOption, ThemePalette

### Community 3 - "PdfToEpubConverter Cluster"
Cohesion: 0.11
Nodes (3): MlKitPdfToEpubConverter, PdfPageContent, PdfToEpubConverter

### Community 4 - "SettingsScreen Cluster"
Cohesion: 0.11
Nodes (4): HsvColor, SettingsTab, ThemeEditorDraft, ThemeEditorSession

### Community 5 - "ImportRouting Cluster"
Cohesion: 0.12
Nodes (9): ArchiveImportCandidate, ArchiveInspectionResult, Candidate, DirectEpubContainer, ImportFailureReason, ImportInspectionResult, ImportRequest, Ready (+1 more)

### Community 6 - "ReaderScreenControls Cluster"
Cohesion: 0.12
Nodes (1): ReaderControlsTab

### Community 7 - "EpubParserBooks Cluster"
Cohesion: 0.14
Nodes (1): PdfDocumentInfo

### Community 8 - "SettingsManagerJson Cluster"
Cohesion: 0.17
Nodes (0): 

### Community 9 - "AppNavigationOperations Cluster"
Cohesion: 0.18
Nodes (4): Duplicate, Failed, ImportBookResult, Imported

### Community 10 - "LibraryModels Cluster"
Cohesion: 0.18
Nodes (8): BookFormat, BookRepresentation, ChapterElement, ConversionStatus, EpubBook, Image, Text, TocItem

### Community 11 - "AppNavigationContracts Cluster"
Cohesion: 0.2
Nodes (9): BookSelectionActionBarActions, BookSelectionActionBarState, BookSelectionUiState, FolderDrawerActions, FolderDrawerUiState, LibraryDialogActions, LibraryDialogState, LibraryScreenActions (+1 more)

### Community 12 - "AppNavigationDialogs Cluster"
Cohesion: 0.2
Nodes (0): 

### Community 13 - "MainActivity Cluster"
Cohesion: 0.22
Nodes (2): MainActivity, Screen

### Community 14 - "EpubParserChapter Cluster"
Cohesion: 0.22
Nodes (0): 

### Community 15 - "SettingsManagerContracts Cluster"
Cohesion: 0.22
Nodes (2): BookProgressPreferenceKeys, SettingsPreferenceKeys

### Community 16 - "PdfReaderScreen Cluster"
Cohesion: 0.25
Nodes (1): PdfDocumentHandle

### Community 17 - "AppNavigationLibraryData Cluster"
Cohesion: 0.29
Nodes (1): FolderDragPreviewUpdate

### Community 18 - "AppNavigationLibrary Cluster"
Cohesion: 0.33
Nodes (0): 

### Community 19 - "AppNavigationStartup Cluster"
Cohesion: 0.33
Nodes (1): AppShellStartupDecision

### Community 20 - "AppLog Cluster"
Cohesion: 0.33
Nodes (1): AppLog

### Community 21 - "ReaderScreenChrome Cluster"
Cohesion: 0.33
Nodes (0): 

### Community 22 - "ReaderScreenContracts Cluster"
Cohesion: 0.33
Nodes (4): ReaderChromeCallbacks, ReaderChromeState, ReaderTheme, TocSort

### Community 23 - "LibraryCards Cluster"
Cohesion: 0.5
Nodes (0): 

### Community 24 - "check_graph_staleness Cluster"
Cohesion: 1.0
Nodes (2): check_staleness(), get_last_modified()

### Community 25 - "AppNavigation Cluster"
Cohesion: 1.0
Nodes (0): 

### Community 26 - "ReaderScreen Cluster"
Cohesion: 1.0
Nodes (0): 

## Knowledge Gaps
- **48 isolated node(s):** `Screen`, `LibraryScreenState`, `BookSelectionUiState`, `FolderDrawerUiState`, `LibraryScreenActions` (+43 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `AppNavigation Cluster`** (2 nodes): `AppNavigation()`, `AppNavigation.kt`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `ReaderScreen Cluster`** (2 nodes): `ReaderScreen.kt`, `ReaderScreen()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What connects `Screen`, `LibraryScreenState`, `BookSelectionUiState` to the rest of the system?**
  _48 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `EpubParser Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.06 - nodes in this community are weakly interconnected._
- **Should `SettingsManager Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._
- **Should `SettingsModels Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `PdfToEpubConverter Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `SettingsScreen Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `ImportRouting Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.12 - nodes in this community are weakly interconnected._