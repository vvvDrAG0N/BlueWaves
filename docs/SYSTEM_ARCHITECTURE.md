# System Architecture Overview

Blue Waves uses a dual-engine architecture: **V1 (Epublib)** for stable EPUB reading and **V2 (Titan)** for high-performance volumetric media (Webnovels, Manga, Anime).

## 1. Graph-First Traversal
For all repo-wide work, start with the graph:
1. `docs/project_graph.md`
2. `graphify-out/GRAPH_REPORT.md`
3. `graphify query "<question>" --budget 1200` if scope is unclear.

## 2. Core Philosophy
*   **Reactive Single-Activity**: One `MainActivity.kt` hosting everything via state-based navigation.
*   **Manual DI**: Dependencies (`SettingsManager`, `EpubParser`, `WavesEngine`) are passed manually through constructors/functions.
*   **Lego Philosophy**: Build modular, atomic components before assembly.
*   **Atomic Files**: Files are kept under 500 lines by splitting concerns into `*Operations.kt`, `*Contracts.kt`, and `*UI.kt`.

## 3. Layer Breakdown

### Entry Point (`MainActivity.kt`)
*   App bootstrap and edge-to-edge setup.
*   The `Screen` enum: Determines which top-level shell is visible (`AppNavigation` vs `TitanOceanShell`).

### V1 Core (Stable EPUB)
*   **App Shell**: `com.epubreader.app.AppNavigation` (Managed via `app/` helper files).
*   **Reader**: `com.epubreader.feature.reader.ReaderScreen` (Managed via `feature/reader/` helper files).
*   **Parser**: `com.epubreader.data.parser.EpubParser` (Managed via `data/parser/` helper files).

### V2 Core (Titan / Volumetric)
*   **Engine**: `WavesEngine.kt` (JNI Bridge to C++ Titan Core).
*   **Ocean Shell**: `com.epubreader.engine.ui.TitanOceanShell` (The volumetric 3D media center).
*   **Scraper**: `ScraperViewModel.kt` (Hybrid networking using JNI and Kotlin Coroutines).

### Shared Layers
*   **Models**: `core/model/` (Immutable shared contracts).
*   **Settings**: `data/settings/SettingsManager.kt` (DataStore source of truth).
*   **UI Elements**: `core/ui/` (Presentation-only components).

## 4. Architectural Rules
*   **Navigation**: State-based through the `Screen` enum. No Navigation Component.
*   **Persistence**: `SettingsManager` is the ONLY place for persistent state.
*   **Titan Native**: All heavy IO or rendering must be native (C++ via JNI) and called from `Dispatchers.IO`.
*   **Position Restoration**: Sacred in V1. Never modify the restoration sequence without full validation.

## 5. Storage Schema
*   **Cache**: `cacheDir/books/{id}/`
*   **V1 Metadata**: `metadata.json` in book folder.
*   **Preferences**: `epub_settings` (Jetpack DataStore).
