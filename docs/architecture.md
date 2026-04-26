# Architecture Overview

Blue Waves is a modular single-activity Android app with manual dependency passing, graph-first docs, and a builder-first compile-time surface-plugin architecture.

## Product Boundary

- The active shell imports and opens EPUB only.
- PDF-origin books may still appear in library metadata and scans.
- Active PDF open/import remains intentionally disabled until a future explicit refactor.

## Module Graph

- `:app`
  - `MainActivity` bootstrap plus `AppNavigation` builder routing and shell assembly.
- `:core:model`
  - Shared pure models, theme contracts, and edit-book request models.
- `:core:ui`
  - Shared Compose presentation helpers, reusable UI primitives, the `SurfacePlugin` contract, and extension-host helpers.
- `:data:settings`
  - `SettingsManager`, key/default mapping, and JSON-backed folder metadata helpers.
- `:data:books`
  - `EpubParser`, route-id lookup helpers, metadata/chapter/editing helpers, and parser-side PDF legacy seams.
- `:feature:library`
  - `LibrarySurfacePlugin` plus library-local data, operations, and UI helper legos.
- `:feature:reader`
  - `ReaderSurfacePlugin`, reader state owner, chrome, controls, and text-selection UI.
- `:feature:settings`
  - `SettingsSurfacePlugin`, reader/settings surfaces, and theme-management UI.
- `:feature:editbook`
  - `EditBookSurfacePlugin` plus EPUB metadata, cover, and chapter mutation UI.
- `:feature:pdf-legacy`
  - `PdfLegacyLegoPlugin` and parked legacy runtime kept outside the active shell.

Repo guard:
- `checkKotlinFileLineLimit` fails when Kotlin files under `src/main`, `src/test`, or `src/androidTest` exceed 500 lines.

## Builder + Surface Contract

- `:app` is the builder.
  - It owns typed routes, startup state, dependency assembly, shell chrome, surface resolution, and feature event handling.
- Each active top-level feature module exposes one root surface plugin.
  - The public surface is `Route + Dependencies + Event + SurfacePlugin`.
- Builder routes carry stable IDs instead of full mutable feature state.
  - `Reader` and `Edit Book` routes carry `bookId`, and the owning feature loads the book from `EpubParser`.
- Failure ownership stays in the feature surface layer.
  - If a feature breaks, fix the surface plugin or its internal helper legos instead of teaching the builder feature logic.
- Surfaces are compile-time registered.
  - `AppSurfaceRegistry` plus `SurfaceRegistry` are code-organization boundaries, not runtime plugin loading.

## Runtime Layers

### Entry Layer

`MainActivity.kt`
- Owns bootstrap, edge-to-edge setup, and app theme selection.
- Must stay small. Do not move app-shell state back into it.

### App Shell

`AppRoute.kt`
- Builder surface route map. Keep payloads lightweight and stable.

`AppSurfaceRegistry.kt`
- Compile-time map from route to surface plugin, shell chrome, and the unavailable fallback surface.

`AppNavigation.kt`
- Owns top-level route selection, startup state, dependency assembly, and feature event handling.

`AppNavigationStartupEffect.kt`
- Startup/version/changelog decision helpers.

`AppNavigationScreenHost.kt`
- Mounts the active resolved surface and keeps transitions in the builder.

`AppNavigationEffects.kt`
- Shell-wide side effects such as haptics and system-bar behavior.

`AppWarmUpScreen.kt`
- Builder-owned startup overlay only.

### Data Layer

`SettingsManagerContracts.kt`
- Key/default map and `Preferences` to model mappers.

`SettingsManager.kt`
- Public persistence API and all DataStore edit transactions.

`SettingsManagerJson.kt`
- Folder-order, folder-sort, and group JSON helpers.

`EpubParser.kt`
- Public parser facade used by the builder and features.

`EpubParserLookup.kt`
- Stable lookup helpers for `bookId`-based routes.

`EpubParserBooks.kt`
- Book ID generation, metadata cache, cover extraction, and TOC rebuild.

`EpubParserEditing.kt`
- Staged EPUB mutation for the Edit Book flow.

`EpubParserChapter.kt`
- Chapter parsing, image lookup, malformed XHTML tolerance, and `normalizePath()`.

`PdfLegacyBridge.kt`
- Parser-side seam for parked PDF runtime work.

### Feature Layer

`feature/library/LibrarySurfacePlugin.kt`
- Public library surface boundary for the builder.

`feature/library/internal/*`
- Library-local data derivation, import/delete/folder operations, state mutation helpers, and UI helper legos.

`feature/settings/SettingsSurfacePlugin.kt`
- Public settings surface boundary.

`SettingsScreen.kt` plus split settings files
- Global reader settings, theme management, import/export, and related UI.

`feature/reader/ReaderSurfacePlugin.kt`
- Public reader surface boundary. Resolves `bookId`, guards the EPUB-only runtime, and mounts `ReaderScreen`.

`ReaderScreen.kt`
- Reader state owner. Keeps chapter loading, restoration, save-progress, and navigation effects.

`ReaderScreenContracts.kt`
- Reader contract map plus theme/helper types.

`ReaderScreenChrome.kt`
- Drawer, top bar, overlays, and shell layout.

`ReaderScreenControls.kt`, `ReaderChapterContent.kt`, `ReaderControlsSections.kt`, `ReaderControlsWidgets.kt`, `ReaderVerticalScrubber.kt`
- Reader controls, chapter rendering, widgets, and scrubber support.

`feature/editbook/EditBookSurfacePlugin.kt`
- Public edit-book surface boundary. Resolves `bookId`, owns loading/unavailable/save states, and mounts `EditBookScreen`.

`EditBookScreen.kt`
- EPUB-only editor for metadata, cover, and chapter mutation flows.

`feature/pdf-legacy/PdfLegacyLegoPlugin.kt`
- Public parked PDF plugin boundary.

`feature/pdf-legacy/*`
- Parked runtime only. Not part of the active shell flow.

### Shared Layer

`core/model/*`
- `EpubBook`, `TocItem`, `ChapterElement`, `GlobalSettings`, `BookProgress`, theme contracts, and edit-book models.

`core/ui/*`
- Shared presentation helpers such as library cards, reader UI support, `SurfacePlugin`, and extension-host helpers.

`core/debug/AppLog.kt`
- Shared logging surface for high-signal diagnostics.

## Ownership Rules

- `:app` is the builder. It assembles and routes, and must not become a feature-logic dumping ground.
- Builder state should stay route-oriented. Do not pass full mutable feature models upward just to make navigation easier.
- `:feature:*` owns feature-specific UI and orchestration.
- Root feature boundaries should stay typed and domain-named: route, dependencies, events, and one root surface plugin.
- `:core:model` owns shared pure contracts and models.
- `:core:ui` owns shared presentation-only helpers.
- `:data:*` owns persistence, parsing, storage, and runtime behavior.
- Do not use feature-to-feature imports for convenience reuse. Extract shared code downward.

## Loading Hints

- Repo-wide routing -> `docs/project_graph.md`
- App shell work -> `docs/app_shell_navigation.md`
- Builder/surface seams -> `app/AppRoute.kt`, `app/AppSurfaceRegistry.kt`, `app/AppNavigation.kt`
- Settings or progress persistence -> `docs/settings_persistence.md`
- Parser or EPUB mutation work -> `docs/epub_parsing.md`
- Reader work -> `docs/reader_screen.md`
- High-risk behavior changes -> `docs/ai_mental_model.md`
- Verification selection -> `docs/test_checklist.md`

## High-Risk Zones

- `feature/reader/ReaderScreen.kt`
  - Restoration timing, save-progress gating, and overscroll navigation.
- `feature/library/internal/LibraryFeatureContent.kt`
  - Library orchestration, dialog flows, and builder-facing event emission.
- `data/settings/SettingsManager.kt`
  - DataStore keys/defaults, folder metadata, and progress integrity.
- `data/parser/EpubParserBooks.kt` / `EpubParserChapter.kt` / `EpubParserLookup.kt`
  - `buildBookId(...)`, `metadata.json`, ZIP safety, route lookup, and `normalizePath()`.
- `data/parser/EpubParserEditing.kt`
  - Atomic EPUB rewrites for the Edit Book flow.
