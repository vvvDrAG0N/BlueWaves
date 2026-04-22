# Settings Persistence Guide

Use this guide when the task is about global settings, custom themes, folder metadata, library sort/favorite state, or per-book progress persistence.

## Fast Load Order

1. `data/settings/SettingsManagerContracts.kt`
   - Key/default map and model-shape helpers.
2. `data/settings/SettingsManager.kt`
   - Public persistence behavior and DataStore edit transactions.
3. `data/settings/SettingsManagerJson.kt`
   - Folder/order/group JSON helpers only when that metadata path matters.
4. `feature/reader/ReaderScreen.kt`
   - Only if the task also changes progress save/restore semantics.

## Ownership Boundaries

- `SettingsManager.kt`
  - Only public persistence entry point.
  - Owns all DataStore edit transactions.
- `SettingsManagerContracts.kt`
  - Owns keys, defaults, theme parsing, and `Preferences` to model mapping.
- `SettingsManagerJson.kt`
  - Owns JSON mutation helpers for folder sorts, folder order, and book groups.

## Safe Edit Rules

- Do not rename preference keys.
- Do not change defaults or schema shape without explicit approval.
- Keep built-in theme IDs backward-compatible.
- Treat folder/order/group JSON as schema-adjacent even though it lives in Preferences.
- If progress semantics change, verify reader save/restore expectations against the new shape.

## Common Tasks

- Change a default setting:
  - Start in `SettingsManagerContracts.kt`.
- Change write behavior:
  - Start in `SettingsManager.kt`.
- Debug theme persistence:
  - Start in `SettingsManagerContracts.kt`, then inspect `SettingsManager.kt`.
- Debug folder rename/delete/order behavior:
  - Start in `SettingsManager.kt`, then open `SettingsManagerJson.kt`.
- Debug progress shape or fallback behavior:
  - Start in `SettingsManagerContracts.kt`, then inspect `SettingsManager.kt`.

## Performance Notes

- Keep `globalSettings` projection narrow so per-book progress writes do not rebuild unrelated global state.
- Prefer the batched helpers for multi-item library mutations so one logical action stays inside one `DataStore.edit {}` transaction.
