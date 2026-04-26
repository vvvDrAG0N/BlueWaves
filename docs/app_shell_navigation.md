# App Shell Navigation Guide

Use this guide when the task is about builder routing, library navigation, startup/version checks, surface switching, or the disabled PDF shell boundary.

## Fast Load Order

1. `app/AppRoute.kt` for the typed builder route surface.
2. `app/AppSurfaceRegistry.kt` for the compile-time surface registry and unavailable fallback.
3. `app/AppNavigation.kt` for route state, startup coordination, and feature event handling.
4. `app/AppNavigationScreenHost.kt` for resolved-surface mounting and transition behavior.
5. One focused plugin/effect file only if the task clearly lives there:
   - `app/AppNavigationStartupEffect.kt`
   - `app/AppNavigationEffects.kt`
   - `feature/library/LibrarySurfacePlugin.kt`
   - `feature/pdf-legacy/PdfLegacyLegoPlugin.kt`

## Ownership Boundaries

- `AppRoute.kt`
  - Defines builder-owned surface routes. Route payloads stay small and stable, such as `bookId`.
- `AppSurfaceRegistry.kt`
  - Resolves routes to root surface plugins, shell chrome, and the shell-owned unavailable fallback surface.
- `AppNavigation.kt`
  - Owns current route, startup state, dependency assembly, and feature event handling.
- `AppNavigationScreenHost.kt`
  - Mounts the active resolved surface and keeps transitions in the builder.
- `AppNavigationStartupEffect.kt`
  - Computes first-run, version, and changelog decisions for the library startup presentation.
- `AppNavigationEffects.kt`
  - Owns shell-wide side effects such as haptics and system bars.
- `feature/library/LibrarySurfacePlugin.kt`
  - Owns library feature orchestration. Folder derivation, dialogs, import/delete, selection, and library rendering live behind this boundary.
- `feature/settings/SettingsSurfacePlugin.kt`
  - Owns settings feature mounting and back events.
- `feature/reader/ReaderSurfacePlugin.kt`
  - Resolves `bookId` into the reader runtime and keeps reader behavior out of the builder.
- `feature/editbook/EditBookSurfacePlugin.kt`
  - Resolves `bookId` into the edit-book runtime and owns save/unavailable states.
- `feature/pdf-legacy/PdfLegacyLegoPlugin.kt`
  - Keeps the parked PDF runtime explicit and outside the active builder flow.

## Safe Edit Rules

- Keep persistence ownership in `SettingsManager`.
- Keep EPUB file/runtime ownership in `EpubParser`.
- Keep navigation state-based through `AppRoute(surfaceId, routeArgs)`.
- Keep the builder innocent: it should route and assemble, not derive feature data or own feature failures.
- Keep route payloads to stable IDs or lightweight flags instead of full `EpubBook` objects.
- Do not move reader restoration behavior into the app shell.
- Prefer feature-local helper extraction over adding more shell state.

## Common Tasks

- Folder ordering, visibility, or drag-preview bugs:
  - Start in `feature/library/internal/LibraryFeatureData.kt`, then inspect `LibraryFeatureContent.kt`.
- Import, delete, last-read, or edit-book save behavior:
  - Start in the owning surface plugin (`LibrarySurfacePlugin.kt`, `ReaderSurfacePlugin.kt`, or `EditBookSurfacePlugin.kt`) and then inspect the specific feature helper file.
- Drawer, grid, or selection-bar layout:
  - Start in `feature/library/internal/ui/LibraryScreen.kt`.
- Sort sheet, confirm dialogs, or welcome/changelog dialogs:
  - Start in `feature/library/internal/ui/LibraryDialogs.kt`.
- Startup/version behavior:
  - Start in `AppNavigationStartupEffect.kt`.
- Disabled PDF behavior:
  - Start in `AppNavigation.kt`, `AppSurfaceRegistry.kt`, and `feature/pdf-legacy/PdfLegacyLegoPlugin.kt`.

## Back Behavior Notes

- The builder only handles feature-level back events after a surface has exhausted its own local back layers.
- In the library surface, folder-selection mode still has higher back priority than drawer dismissal.
- The library surface should clear selection-mode state before asking the builder to leave the library surface.
