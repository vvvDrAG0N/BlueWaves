# Graph Report - C:\Users\Amon\Desktop\projects\Epub_Reader_v2  (2026-04-12)

## Corpus Check
- 23 files · ~35,555 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 163 nodes · 140 edges · 23 communities detected
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_SettingsManager Cluster|SettingsManager Cluster]]
- [[_COMMUNITY_SettingsManagerJson Cluster|SettingsManagerJson Cluster]]
- [[_COMMUNITY_AppNavigationOperations Cluster|AppNavigationOperations Cluster]]
- [[_COMMUNITY_AppNavigationContracts Cluster|AppNavigationContracts Cluster]]
- [[_COMMUNITY_AppNavigationDialogs Cluster|AppNavigationDialogs Cluster]]
- [[_COMMUNITY_EpubParser Cluster|EpubParser Cluster]]
- [[_COMMUNITY_EpubParserChapter Cluster|EpubParserChapter Cluster]]
- [[_COMMUNITY_LibraryModels Cluster|LibraryModels Cluster]]
- [[_COMMUNITY_ReaderScreenControls Cluster|ReaderScreenControls Cluster]]
- [[_COMMUNITY_AppNavigationLibraryData Cluster|AppNavigationLibraryData Cluster]]
- [[_COMMUNITY_EpubParserBooks Cluster|EpubParserBooks Cluster]]
- [[_COMMUNITY_AppNavigationLibrary Cluster|AppNavigationLibrary Cluster]]
- [[_COMMUNITY_AppNavigationStartup Cluster|AppNavigationStartup Cluster]]
- [[_COMMUNITY_AppLog Cluster|AppLog Cluster]]
- [[_COMMUNITY_ReaderScreenChrome Cluster|ReaderScreenChrome Cluster]]
- [[_COMMUNITY_ReaderScreenContracts Cluster|ReaderScreenContracts Cluster]]
- [[_COMMUNITY_MainActivity Cluster|MainActivity Cluster]]
- [[_COMMUNITY_SettingsManagerContracts Cluster|SettingsManagerContracts Cluster]]
- [[_COMMUNITY_SettingsScreen Cluster|SettingsScreen Cluster]]
- [[_COMMUNITY_SettingsModels Cluster|SettingsModels Cluster]]
- [[_COMMUNITY_LibraryCards Cluster|LibraryCards Cluster]]
- [[_COMMUNITY_AppNavigation Cluster|AppNavigation Cluster]]
- [[_COMMUNITY_ReaderScreen Cluster|ReaderScreen Cluster]]

## God Nodes (most connected - your core abstractions)
1. `SettingsManager` - 16 edges
2. `EpubParser` - 9 edges
3. `AppLog` - 5 edges
4. `Image` - 3 edges
5. `MainActivity` - 2 edges
6. `Screen` - 1 edges
7. `LibraryScreenState` - 1 edges
8. `BookSelectionUiState` - 1 edges
9. `FolderDrawerUiState` - 1 edges
10. `LibraryScreenActions` - 1 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Communities

### Community 0 - "SettingsManager Cluster"
Cohesion: 0.12
Nodes (1): SettingsManager

### Community 1 - "SettingsManagerJson Cluster"
Cohesion: 0.17
Nodes (0): 

### Community 2 - "AppNavigationOperations Cluster"
Cohesion: 0.18
Nodes (4): Duplicate, Failed, ImportBookResult, Imported

### Community 3 - "AppNavigationContracts Cluster"
Cohesion: 0.2
Nodes (9): BookSelectionActionBarActions, BookSelectionActionBarState, BookSelectionUiState, FolderDrawerActions, FolderDrawerUiState, LibraryDialogActions, LibraryDialogState, LibraryScreenActions (+1 more)

### Community 4 - "AppNavigationDialogs Cluster"
Cohesion: 0.2
Nodes (0): 

### Community 5 - "EpubParser Cluster"
Cohesion: 0.2
Nodes (1): EpubParser

### Community 6 - "EpubParserChapter Cluster"
Cohesion: 0.22
Nodes (0): 

### Community 7 - "LibraryModels Cluster"
Cohesion: 0.25
Nodes (5): ChapterElement, EpubBook, Image, Text, TocItem

### Community 8 - "ReaderScreenControls Cluster"
Cohesion: 0.25
Nodes (0): 

### Community 9 - "AppNavigationLibraryData Cluster"
Cohesion: 0.29
Nodes (1): FolderDragPreviewUpdate

### Community 10 - "EpubParserBooks Cluster"
Cohesion: 0.29
Nodes (0): 

### Community 11 - "AppNavigationLibrary Cluster"
Cohesion: 0.33
Nodes (0): 

### Community 12 - "AppNavigationStartup Cluster"
Cohesion: 0.33
Nodes (1): AppShellStartupDecision

### Community 13 - "AppLog Cluster"
Cohesion: 0.33
Nodes (1): AppLog

### Community 14 - "ReaderScreenChrome Cluster"
Cohesion: 0.33
Nodes (0): 

### Community 15 - "ReaderScreenContracts Cluster"
Cohesion: 0.33
Nodes (4): ReaderChromeCallbacks, ReaderChromeState, ReaderTheme, TocSort

### Community 16 - "MainActivity Cluster"
Cohesion: 0.4
Nodes (2): MainActivity, Screen

### Community 17 - "SettingsManagerContracts Cluster"
Cohesion: 0.4
Nodes (2): BookProgressPreferenceKeys, SettingsPreferenceKeys

### Community 18 - "SettingsScreen Cluster"
Cohesion: 0.5
Nodes (0): 

### Community 19 - "SettingsModels Cluster"
Cohesion: 0.67
Nodes (2): BookProgress, GlobalSettings

### Community 20 - "LibraryCards Cluster"
Cohesion: 0.67
Nodes (0): 

### Community 21 - "AppNavigation Cluster"
Cohesion: 1.0
Nodes (0): 

### Community 22 - "ReaderScreen Cluster"
Cohesion: 1.0
Nodes (0): 

## Knowledge Gaps
- **28 isolated node(s):** `Screen`, `LibraryScreenState`, `BookSelectionUiState`, `FolderDrawerUiState`, `LibraryScreenActions` (+23 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `AppNavigation Cluster`** (2 nodes): `AppNavigation()`, `AppNavigation.kt`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `ReaderScreen Cluster`** (2 nodes): `ReaderScreen.kt`, `ReaderScreen()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What connects `Screen`, `LibraryScreenState`, `BookSelectionUiState` to the rest of the system?**
  _28 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `SettingsManager Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.12 - nodes in this community are weakly interconnected._