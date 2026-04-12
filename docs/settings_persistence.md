# Settings Persistence Guide

This file is a low-context map for AI agents working in the DataStore-backed persistence layer.

## Purpose

Use this guide when the task is about global settings, folder metadata, library sort/favorite state, or per-book progress persistence.

## Fast Load Order

1. Read `data/settings/SettingsManagerContracts.kt` for the key/default map and data-shape helpers.
2. Read `data/settings/SettingsManager.kt` for public persistence behavior and DataStore edit transactions.
3. Read `data/settings/SettingsManagerJson.kt` only if the task touches folder-order, folder-sort, or book-group JSON behavior.
4. Read `feature/reader/ReaderScreen.kt` only if the task also touches progress save/restore timing.

## Ownership Boundaries

- `SettingsManager.kt`
  - Public persistence API.
  - Owns DataStore edit transactions.
  - Coordinates folder metadata writes, global setting writes, and progress writes.

- `SettingsManagerContracts.kt`
  - Package-private key/default map.
  - Maps `Preferences` to `GlobalSettings` and `BookProgress`.
  - Defines book-progress preference key bundles.

- `SettingsManagerJson.kt`
  - Package-private JSON helpers for folder sorts, folder order, and book groups.
  - Keeps safe-vs-strict JSON parsing behavior explicit.

## Safe Edit Rules

- Do not change preference key names.
- Do not change default values unless the task explicitly requires a migration.
- Keep `SettingsManager` as the only public persistence entry point.
- Treat folder/order/group JSON behavior as schema-adjacent even though the schema is still Preferences-backed.
- If the task affects reader progress semantics, verify that `ReaderScreen` expectations still match the saved values.

## Common Tasks

- Change a default setting value:
  - Start in `SettingsManagerContracts.kt`.

- Change how settings are written:
  - Start in `SettingsManager.kt`.

- Change folder rename/delete/order behavior:
  - Start in `SettingsManager.kt`, then open `SettingsManagerJson.kt`.

- Debug wrong favorite library, wrong sort, or missing folder assignment:
  - Start in `SettingsManagerContracts.kt`, then inspect `SettingsManager.kt`.

- Debug reading progress save/load shape:
  - Start in `SettingsManagerContracts.kt`, then inspect `SettingsManager.kt`.

## Known Remaining Coupling

- `SettingsManager.kt` still owns all write transactions to keep behavior centralized.
- Folder metadata still uses JSON strings inside Preferences, so schema meaning is spread across write helpers and callers.
- A later phase could separate folder metadata operations from global setting operations, but that would be a bigger architectural move.
