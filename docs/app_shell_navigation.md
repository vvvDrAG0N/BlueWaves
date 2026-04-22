# App Shell Navigation Guide

Use this guide when the task is about library navigation, folder management, dialogs, startup/version checks, screen switching, or the disabled PDF shell boundary.

## Fast Load Order

1. `app/AppNavigationContracts.kt` for the shell state/action surface.
2. `app/AppNavigation.kt` for screen routing, shell state, and coordination behavior.
3. One focused helper/rendering file only if the task clearly lives there:
   - `AppNavigationStartup.kt`
   - `AppNavigationOperations.kt`
   - `AppNavigationLibraryData.kt`
   - `AppNavigationLibrary.kt`
   - `AppNavigationDialogs.kt`
   - `AppNavigationPdfLegacy.kt`

## Ownership Boundaries

- `AppNavigation.kt`
  - Owns transient shell state, screen routing, startup effects, and top-level action lambdas.
- `AppNavigationContracts.kt`
  - Defines shell-private bundled contracts for rendering and dialogs.
- `AppNavigationStartup.kt`
  - Computes first-run, version, and changelog decisions.
- `AppNavigationOperations.kt`
  - Owns import, delete, last-read touch, edit-book save coordination, and other shell side effects.
- `AppNavigationLibraryData.kt`
  - Owns pure folder derivation, sorting, JSON parsing, and drag-preview helpers.
- `AppNavigationLibrary.kt`
  - Owns drawer, top bar, grid, and selection action bar rendering only.
- `AppNavigationDialogs.kt`
  - Owns sort sheet and library-level dialogs only.
- `AppNavigationPdfLegacy.kt`
  - Owns the tiny shell-side bridge for the intentionally disabled PDF boundary.

## Safe Edit Rules

- Keep persistence ownership in `SettingsManager`.
- Keep EPUB file/runtime ownership in `EpubParser`.
- Do not change `Screen`-based navigation.
- Do not move reader restoration behavior into the app shell.
- Prefer pure helper extraction over adding more shell state.

## Common Tasks

- Folder ordering, visibility, or drag-preview bugs:
  - Start in `AppNavigationLibraryData.kt`, then inspect `AppNavigation.kt`.
- Import, delete, last-read, or edit-book save behavior:
  - Start in `AppNavigationOperations.kt`, then inspect `AppNavigation.kt`.
- Drawer, grid, or selection-bar layout:
  - Start in `AppNavigationLibrary.kt`.
- Sort sheet, confirm dialogs, or welcome/changelog dialogs:
  - Start in `AppNavigationDialogs.kt`.
- Startup/version behavior:
  - Start in `AppNavigationStartup.kt`.
- Disabled PDF behavior:
  - Start in `AppNavigation.kt` and `AppNavigationPdfLegacy.kt`.

## Back Behavior Notes

- In the library drawer, folder-selection mode has higher back priority than drawer dismissal.
- Back should clear selection-mode state before leaving the library surface.
