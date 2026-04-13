# Graph Report - C:\Users\Amon\Desktop\projects\Epub_Reader_v2  (2026-04-14)

## Corpus Check
- 24 files · ~45,510 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 214 nodes · 191 edges · 24 communities detected
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_SettingsManager Cluster|SettingsManager Cluster]]
- [[_COMMUNITY_SettingsModels Cluster|SettingsModels Cluster]]
- [[_COMMUNITY_SettingsScreen Cluster|SettingsScreen Cluster]]
- [[_COMMUNITY_ReaderScreenControls Cluster|ReaderScreenControls Cluster]]
- [[_COMMUNITY_app_src_main_java_com_epubreader_data_settings_settingsmanagerjson_kt Cluster|app_src_main_java_com_epubreader_data_settings_settingsmanagerjson_kt Cluster]]
- [[_COMMUNITY_AppNavigationOperations Cluster|AppNavigationOperations Cluster]]
- [[_COMMUNITY_app_src_main_java_com_epubreader_app_appnavigationcontracts_kt Cluster|app_src_main_java_com_epubreader_app_appnavigationcontracts_kt Cluster]]
- [[_COMMUNITY_AppNavigationDialogs Cluster|AppNavigationDialogs Cluster]]
- [[_COMMUNITY_epubparser_epubparser Cluster|epubparser_epubparser Cluster]]
- [[_COMMUNITY_MainActivity Cluster|MainActivity Cluster]]
- [[_COMMUNITY_EpubParserChapter Cluster|EpubParserChapter Cluster]]
- [[_COMMUNITY_app_src_main_java_com_epubreader_core_model_librarymodels_kt Cluster|app_src_main_java_com_epubreader_core_model_librarymodels_kt Cluster]]
- [[_COMMUNITY_AppNavigationLibraryData Cluster|AppNavigationLibraryData Cluster]]
- [[_COMMUNITY_EpubParserBooks Cluster|EpubParserBooks Cluster]]
- [[_COMMUNITY_SettingsManagerContracts Cluster|SettingsManagerContracts Cluster]]
- [[_COMMUNITY_AppNavigationLibrary Cluster|AppNavigationLibrary Cluster]]
- [[_COMMUNITY_AppNavigationStartup Cluster|AppNavigationStartup Cluster]]
- [[_COMMUNITY_AppLog Cluster|AppLog Cluster]]
- [[_COMMUNITY_ReaderScreenChrome Cluster|ReaderScreenChrome Cluster]]
- [[_COMMUNITY_ReaderScreenContracts Cluster|ReaderScreenContracts Cluster]]
- [[_COMMUNITY_app_src_main_java_com_epubreader_core_ui_librarycards_kt Cluster|app_src_main_java_com_epubreader_core_ui_librarycards_kt Cluster]]
- [[_COMMUNITY_check_graph_staleness Cluster|check_graph_staleness Cluster]]
- [[_COMMUNITY_AppNavigation Cluster|AppNavigation Cluster]]
- [[_COMMUNITY_ReaderScreen Cluster|ReaderScreen Cluster]]

## God Nodes (most connected - your core abstractions)
1. `SettingsManager` - 21 edges
2. `EpubParser` - 9 edges
3. `AppLog` - 5 edges
4. `Image` - 3 edges
5. `MainActivity` - 2 edges
6. `HsvColor` - 2 edges
7. `ThemeEditorDraft` - 2 edges
8. `get_last_modified()` - 2 edges
9. `check_staleness()` - 2 edges
10. `Screen` - 1 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Communities

### Community 0 - "SettingsManager Cluster"
Cohesion: 0.09
Nodes (1): SettingsManager

### Community 1 - "SettingsModels Cluster"
Cohesion: 0.11
Nodes (5): BookProgress, CustomTheme, GlobalSettings, ThemeOption, ThemePalette

### Community 2 - "SettingsScreen Cluster"
Cohesion: 0.11
Nodes (4): HsvColor, SettingsTab, ThemeEditorDraft, ThemeEditorSession

### Community 3 - "ReaderScreenControls Cluster"
Cohesion: 0.12
Nodes (1): ReaderControlsTab

### Community 4 - "app_src_main_java_com_epubreader_data_settings_settingsmanagerjson_kt Cluster"
Cohesion: 0.17
Nodes (0): 

### Community 5 - "AppNavigationOperations Cluster"
Cohesion: 0.18
Nodes (4): Duplicate, Failed, ImportBookResult, Imported

### Community 6 - "app_src_main_java_com_epubreader_app_appnavigationcontracts_kt Cluster"
Cohesion: 0.2
Nodes (9): BookSelectionActionBarActions, BookSelectionActionBarState, BookSelectionUiState, FolderDrawerActions, FolderDrawerUiState, LibraryDialogActions, LibraryDialogState, LibraryScreenActions (+1 more)

### Community 7 - "AppNavigationDialogs Cluster"
Cohesion: 0.2
Nodes (0): 

### Community 8 - "epubparser_epubparser Cluster"
Cohesion: 0.2
Nodes (1): EpubParser

### Community 9 - "MainActivity Cluster"
Cohesion: 0.22
Nodes (2): MainActivity, Screen

### Community 10 - "EpubParserChapter Cluster"
Cohesion: 0.22
Nodes (0): 

### Community 11 - "app_src_main_java_com_epubreader_core_model_librarymodels_kt Cluster"
Cohesion: 0.25
Nodes (5): ChapterElement, EpubBook, Image, Text, TocItem

### Community 12 - "AppNavigationLibraryData Cluster"
Cohesion: 0.29
Nodes (1): FolderDragPreviewUpdate

### Community 13 - "EpubParserBooks Cluster"
Cohesion: 0.29
Nodes (0): 

### Community 14 - "SettingsManagerContracts Cluster"
Cohesion: 0.29
Nodes (2): BookProgressPreferenceKeys, SettingsPreferenceKeys

### Community 15 - "AppNavigationLibrary Cluster"
Cohesion: 0.33
Nodes (0): 

### Community 16 - "AppNavigationStartup Cluster"
Cohesion: 0.33
Nodes (1): AppShellStartupDecision

### Community 17 - "AppLog Cluster"
Cohesion: 0.33
Nodes (1): AppLog

### Community 18 - "ReaderScreenChrome Cluster"
Cohesion: 0.33
Nodes (0): 

### Community 19 - "ReaderScreenContracts Cluster"
Cohesion: 0.33
Nodes (4): ReaderChromeCallbacks, ReaderChromeState, ReaderTheme, TocSort

### Community 20 - "app_src_main_java_com_epubreader_core_ui_librarycards_kt Cluster"
Cohesion: 0.67
Nodes (0): 

### Community 21 - "check_graph_staleness Cluster"
Cohesion: 1.0
Nodes (2): check_staleness(), get_last_modified()

### Community 22 - "AppNavigation Cluster"
Cohesion: 1.0
Nodes (0): 

### Community 23 - "ReaderScreen Cluster"
Cohesion: 1.0
Nodes (0): 

## Knowledge Gaps
- **34 isolated node(s):** `Screen`, `LibraryScreenState`, `BookSelectionUiState`, `FolderDrawerUiState`, `LibraryScreenActions` (+29 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `AppNavigation Cluster`** (2 nodes): `AppNavigation()`, `AppNavigation.kt`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `ReaderScreen Cluster`** (2 nodes): `ReaderScreen.kt`, `ReaderScreen()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What connects `Screen`, `LibraryScreenState`, `BookSelectionUiState` to the rest of the system?**
  _34 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `SettingsManager Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._
- **Should `SettingsModels Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `SettingsScreen Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `ReaderScreenControls Cluster` be split into smaller, more focused modules?**
  _Cohesion score 0.12 - nodes in this community are weakly interconnected._