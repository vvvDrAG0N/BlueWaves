# App Shell Navigation Guide

Use this guide when the task is about builder routing, library navigation, startup/version checks, screen switching, or the disabled PDF shell boundary.

## Fast Load Order

1. `app/AppRoute.kt` for the typed builder route surface.
2. `app/AppFeatureRegistry.kt` for the compile-time plugin map.
3. `app/AppNavigation.kt` for route state, startup coordination, and plugin event handling.
4. `app/AppNavigationScreenHost.kt` for plugin mounting and transition behavior.
5. One focused plugin/effect file only if the task clearly lives there:
   - `app/AppNavigationStartupEffect.kt`
   - `app/AppNavigationEffects.kt`
   - `feature/library/LibraryLegoPlugin.kt`
   - `feature/pdf/legacy/PdfLegacyLegoPlugin.kt`

## Ownership Boundaries

- `AppRoute.kt`
  - Defines builder-owned routes. Route payloads stay small and stable, such as `bookId`.
- `AppFeatureRegistry.kt`
  - Maps routes to root feature lego/plugins and shell chrome behavior.
- `AppNavigation.kt`
  - Owns current route, startup state, dependency assembly, and feature event handling.
- `AppNavigationScreenHost.kt`
  - Mounts the active feature plugin and keeps transitions in the builder.
- `AppNavigationStartupEffect.kt`
  - Computes first-run, version, and changelog decisions for the library startup presentation.
- `AppNavigationEffects.kt`
  - Owns shell-wide side effects such as haptics and system bars.
- `feature/library/LibraryLegoPlugin.kt`
  - Owns library feature orchestration. Folder derivation, dialogs, import/delete, selection, and library rendering live behind this boundary.
- `feature/settings/SettingsLegoPlugin.kt`
  - Owns settings feature mounting and back events.
- `feature/reader/ReaderLegoPlugin.kt`
  - Resolves `bookId` into the reader runtime and keeps reader behavior out of the builder.
- `feature/editbook/EditBookLegoPlugin.kt`
  - Resolves `bookId` into the edit-book runtime and owns save/unavailable states.
- `feature/pdf/legacy/PdfLegacyLegoPlugin.kt`
  - Keeps the parked PDF runtime explicit and outside the active builder flow.

## Safe Edit Rules

- Keep persistence ownership in `SettingsManager`.
- Keep EPUB file/runtime ownership in `EpubParser`.
- Do not change `Screen`-based navigation.
- Keep the builder innocent: it should route and assemble, not derive feature data or own feature failures.
- Keep route payloads to stable IDs or lightweight flags instead of full `EpubBook` objects.
- Do not move reader restoration behavior into the app shell.
- Prefer feature-local helper extraction over adding more shell state.

## Common Tasks

- Folder ordering, visibility, or drag-preview bugs:
  - Start in `feature/library/internal/LibraryFeatureData.kt`, then inspect `LibraryFeatureContent.kt`.
- Import, delete, last-read, or edit-book save behavior:
  - Start in the owning plugin (`LibraryLegoPlugin.kt`, `ReaderLegoPlugin.kt`, or `EditBookLegoPlugin.kt`) and then inspect the specific feature helper file.
- Drawer, grid, or selection-bar layout:
  - Start in `feature/library/internal/ui/LibraryScreen.kt`.
- Sort sheet, confirm dialogs, or welcome/changelog dialogs:
  - Start in `feature/library/internal/ui/LibraryDialogs.kt`.
- Startup/version behavior:
  - Start in `AppNavigationStartupEffect.kt`.
- Disabled PDF behavior:
  - Start in `AppNavigation.kt`, `AppFeatureRegistry.kt`, and `feature/pdf/legacy/PdfLegacyLegoPlugin.kt`.

## Back Behavior Notes

- The builder only handles feature-level back events after a plugin has exhausted its own local back layers.
- In the library plugin, folder-selection mode still has higher back priority than drawer dismissal.
- The library plugin should clear selection-mode state before asking the builder to leave the library surface.
