# 🌊 Blue Waves V2: Engine Architecture

## 1. The Titan Philosophy
The V2 Engine is a **Native-First Hybrid**. We move all byte-crunching, math, and rendering logic into C++ to achieve "Game Engine" performance (144Hz) and battery efficiency.

### Phase 13: Hybrid Native Networking (Active)
- **TitanScraper Ecosystem**: Established base interfaces for volumetric scrapers in C++.
- **Hybrid Path**: C++ orchestrates search/parsing logic; Kotlin provides IO via JNI callbacks.
- **TitanLog API**: Unified native logging and performance profiling.

### Phase 14: Volumetric Grid Refinement (Active)
- **Tactile Ecosystem**: Integrated `TitanHaptics` for physical feedback on boundaries and transitions.
- **Micro-Animations**: Added entrance and bobbing physics to `VolumetricBookCard`.
- **Haptic Strategy**: `Impact` for boundaries, `Tap` for interactions, `Ping` for result discovery.

## 2. Component Map (Lego Pieces)

| Piece | Type | Responsibility | Contract |
| :--- | :--- | :--- | :--- |
| `ZipEngine` | Native | Decompression (zlib) | `extractEpub` |
| `RendererCore` | Native | Line Breaking / Reflow | `layoutChapter` |
| `WebnovelScraper` | Native | Scrape Strategy (Regex) | `search` |
| `WavesEngine` | Kotlin | Native Shell / IO Bridge | `fetchHtml` |
| `TitanHaptics` | Kotlin | High-Fidelity Feedback | `impact`, `tap` |
| `LiquidOverscroll` | Kotlin | Physics State | `LiquidOverscrollState` |
| `LiquidPhysics` | Kotlin | Spring/Tilt Logic | `3DTransform` |
| `TitanLibraryRepo` | Kotlin | Data Lego: Collection Persistence | `books` Flow |
| `TitanLibraryViewModel` | Kotlin | Logic Lego: Shell-Library Bridge | `removeBook` |
| `TitanReaderViewModel` | Kotlin | Logic Lego: Novel Reader State | `content` Flow |
| `MangaRepository` | Kotlin | Data Lego: Manga Page Streaming | `currentChapter` Flow |
| `MangaImageCache` | Kotlin | Data Lego: High-Speed Image Decoding | `preloadPage` |
| `MangaViewModel` | Kotlin | Logic Lego: Manga Search & Fetch | `search` |
| `MangaReaderViewModel` | Kotlin | Logic Lego: Page State & Scroll Mgmt | `pages` Flow |
| `MangaModels` | Kotlin | Contract Lego: Immutable Manga Data | `MangaPageV2` |
| `AnimeRepository` | Kotlin | Data Lego: Anime Episode Streaming | `currentEpisode` Flow |
| `AnimeModels` | Kotlin | Contract Lego: Immutable Anime Data | `AnimeEpisodeV2` |
| `MusicRepository` | Kotlin | Data Lego: Music Track Streaming | `currentTrack` Flow |
| `MusicModels` | Kotlin | Contract Lego: Immutable Music Data | `MusicTrackV2` |
| `IScraper` | Kotlin | Bridge Lego: Universal Media Scraper | `search` |
| `TitanScraperFactory` | Kotlin | Factory Lego: Scraper Routing | `getScraper` |
| `MangaReader` | Kotlin | UI Lego: Vertical Infinite Image List | `MangaReaderScreen` |

## 3. Phase 3: Liquid Layout Engine (`RendererCore.cpp`)
*   **Mechanism**: Custom Greedy Wrap Algorithm.
*   **State Management**: It stores a `NativeLine` cache containing the exact `y` coordinates and text of the reflowed document.
*   **Hit-Testing**: Because we know the pixel-perfect position of every line in C++, text selection is 100% stable and fast.

## 4. Rendering Pipeline
1.  **Logic (C++)**: Calculates the "Flow" and "Physics."
2.  **Bridge (JNI)**: Sends the frame metadata to Kotlin.
3.  **Visuals (Compose + AGSL)**: Draws the results using high-fidelity shaders.

## 5. Memory Safety
*   All native memory is managed within the `ChapterLayout` lifecycle. 
*   **Rule**: Kotlin never owns native pointers; it only queries indices to prevent memory leaks.

## 6. The Lego Architecture (`IEngineComponent.h`)
*   **Interfaces**: All major components (Sources, Layouts) must implement a virtual interface.
*   **Benefit**: Allows swapping EPUB for PDF or Cloud sources without touching the renderer.

## 7. The Scraper Lego (`IScraper`)
*   **Strategy**: Hybrid Scraper.
*   **Networking**: Handled by Kotlin (for easy cookie/session management).
*   **Parsing**: Handled by C++ (for high-speed DOM traversal and text extraction).
*   **Unified Flow**: Scraped chapters are converted into the standard `NativeLine` format, allowing them to be read with the same visual fidelity as EPUBs.
