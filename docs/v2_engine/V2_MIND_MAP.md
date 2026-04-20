# 🌊 Blue Waves V2: Engine Vision Mind Map

## 1. Core Engine (Native / JNI)
*   **Custom Unzipper**: Native implementation (libzip/Rust) for instant EPUB/Zip access with zero JVM overhead.
*   **Math Engine**: Ultra-fast vector math for wave physics and PDF coordinate transformation.
*   **PDF Reflow Core**: A custom extractor that converts PDF blocks into "Liquid Text" streams in real-time.
*   **Battery Saver**: Offloading all heavy byte-parsing to native threads to minimize CPU wake-ups and GC pressure.

## 2. Visual Engine (Shader-Driven)
*   **Unified Shader Field**: All UI elements (cards, buttons) are integrated into the AGSL background math.
*   **Volumetric Gallery**: A 3D-feeling Library screen where books float in the "Blue Waves" ocean.
*   **Liquid Transitions**: Frame-perfect, 120fps "Melt" navigation between all app modules.
*   **Texture Quality**: High-fidelity caustic and refraction effects optimized for mobile GPUs.

## 3. The Content Source (Scraper API)
*   **Modular Scrapers**: Pluggable modules for Webnovel and Book sources.
*   **Stream Sync**: Background scraping that feeds directly into the Unified Document Model.
*   **Offline First**: Intelligent caching of scraped content into the high-performance native database.

## 4. UI Architecture (Liquid Framework)
*   **Reflow Everything**: A unified renderer that treats EPUB, PDF, and Web as the same "Liquid" data.
*   **Theme Studio V2**: Deep integration where themes change not just colors, but the "Physics" of the ocean (e.g., Viscosity, Light Depth).
*   **Custom Fonts**: Global font injection across all document types (even PDF).

## 6. The Omni-Vision (Future Legos)
*   **Manga Lego**: High-speed texture-streaming engine for infinite-scroll manga reading.
*   **Media Lego**: Native FFmpeg integration for 120fps Anime playback and Hi-Fi Music streaming.
*   **Universal Scraping**: A single `IScraper` interface that powers Books, Manga, and Anime sources.
*   **Unified Ocean**: All media types represented as 3D Volumetric cards floating in the Blue Waves ecosystem.

## 7. Development & Quality Rules
*   **Titan Documentation Protocol**: Mandatory real-time documentation updates for all engine changes.
*   **Phase 1-5**: Core Titan Engine & Liquid Reader.
*   **Phase 6-14**: Volumetric Library & Hybrid Scrapers.
*   **Phase 15**: Manga Lego (Image Strategy).
*   **Phase 16**: Anime Lego (Video Strategy).
*   **Phase 17**: Music Lego (Audio Strategy).
*   **Phase 18**: Universal Scraper (Omni-Bridge).
