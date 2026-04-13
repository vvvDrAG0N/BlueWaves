# App Shell Navigation Guide

This file is a low-context map for AI agents working in the app shell after the AppNavigation safe refactor.

## Purpose

Use this guide when the task is about library navigation, folder management, dialogs, startup version checks, or screen switching.

## Fast Load Order

1. Read `app/AppNavigationContracts.kt` if you only need the state/callback ownership map for the app shell.
2. Read `app/AppNavigation.kt` for state ownership, startup effects, derived folder/book state, and screen routing.
3. Read `app/AppNavigationStartup.kt` only if the task is about first-run logic, version checks, or changelog selection.
4. Read `app/AppNavigationOperations.kt` only if the task is about import, delete, last-read updates, or folder/book mutations.
5. Read `app/AppNavigationLibraryData.kt` only if the task is about folder derivation, sorting, or drag-preview behavior.
6. Read `app/AppNavigationLibrary.kt` only if the task is about drawer UI, top bar UI, grid rendering, or selection bar layout.
7. Read `app/AppNavigationDialogs.kt` only if the task is about sort-sheet behavior, confirm dialogs, or welcome/changelog dialogs.
8. Read `data/settings/SettingsManager.kt` if the task touches persisted folder/order/sort/favorite state.
9. Read `data/parser/EpubParser.kt` only if the task touches import, scan, delete, or last-read persistence.

## Ownership Boundaries

- `AppNavigation.kt`
  - Owns transient app-shell state.
  - Owns startup/version/changelog effects and screen routing.
  - Owns action lambdas that coordinate UI state with `SettingsManager` and `EpubParser`.

- `AppNavigationContracts.kt`
  - Defines app-shell private constants plus state/action contracts for library rendering and dialogs.
  - Use this when you need the shape of the shell without loading full rendering files.

## Architectural Ownership (The "Why")

The 7-file split in `com.epubreader.app` is designed to reduce per-task context load:

1.  **State vs. Derivation**: `AppNavigation.kt` owns the *transient state* (what is selected), while `AppNavigationLibraryData.kt` owns the *logic of derivation* (how folders are built from JSON). This prevents the main shell from becoming a "God Object" of both state and business logic.
2.  **Startup Precedence**: `AppNavigationStartup.kt` enforces the following priority:
    -   **First Run (Empty Library)**: Show Welcome Note.
    -   **Upgrade (Existing Library)**: Show Changelog if `versionCode` has increased since `lastSeenVersionCode`.
    -   **Normal Run**: Go straight to Library.
3.  **Side Effect Isolation**: `AppNavigationOperations.kt` centralizes all destructive or file-system-heavy actions (Import, Delete). This ensures that `AppNavigation.kt` only coordinates the UI response to these effects.

- `AppNavigationStartup.kt`
  - Computes first-run, version, and changelog decisions.
  - Does not write settings itself; `AppNavigation` still applies the results.

- `AppNavigationOperations.kt`
  - Owns app-shell helper operations for import, scan, last-read touches, and destructive library mutations.
  - Delegates persistence to `SettingsManager` and file work to `EpubParser`.

- `AppNavigationLibraryData.kt`
  - Owns pure JSON parsing, folder derivation, library sorting, and folder drag-preview helpers.
  - Start here for folder-order/sort bugs before reading rendering files.

- `AppNavigationLibrary.kt`
  - Renders library drawer, library top bar, library grid, and bottom selection action bar.
  - Should stay mostly presentational.

- `AppNavigationDialogs.kt`
  - Renders sort sheet and library-level dialogs.
  - Should not become a new state owner.

## Safe Edit Rules

- Do not move persistence ownership out of `SettingsManager`.
- Do not move EPUB file operations out of `EpubParser`.
- Do not change `Screen`-based navigation.
- Do not move reader restoration logic into the app shell.
- Prefer adding pure derived helpers or small package-local operations over adding more shell state.

## Common Tasks

- Change folder or book selection behavior:
  - Start in `app/AppNavigation.kt`, then open `app/AppNavigationLibrary.kt`.

- Change drawer visuals, grid visuals, or selection bar visuals:
  - Start in `app/AppNavigationLibrary.kt`.

- Change sort-sheet options, confirmation copy, or welcome/changelog dialogs:
  - Start in `app/AppNavigationDialogs.kt`.

- Debug incorrect folder ordering or sort behavior:
  - Start in `app/AppNavigationLibraryData.kt`, then inspect `app/AppNavigation.kt` and `data/settings/SettingsManager.kt`.

- Debug duplicate import, delete flow, or last-read update:
  - Start in `app/AppNavigationOperations.kt`, then inspect `app/AppNavigation.kt` and `data/parser/EpubParser.kt`.

## Known Remaining Coupling

- `AppNavigation.kt` still coordinates most action lambdas to keep behavior unchanged.
- `AppNavigation.kt` still applies startup decisions and owns the back-handler/screen-routing logic.
- `LibraryScreen` now depends on bundled contracts instead of raw parameter lists, but it still consumes `SettingsManager` indirectly because shared UI components already require it.
