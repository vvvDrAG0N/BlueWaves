# Deprecated PDF Path

This note is legacy context only. It is not part of the active product surface.

## Current Status

- The active shell imports and opens EPUB only.
- `AppNavigation` blocks active PDF open/import flows.
- Existing PDF-origin library entries may still appear so they can be preserved for a future explicit refactor.

## Parked Runtime Files

- `app/AppNavigationPdfLegacy.kt`
- `data/parser/PdfLegacyBridge.kt`
- `data/pdf/legacy/PdfToEpubConverter.kt`
- `data/pdf/legacy/PdfConversionWorker.kt`
- `feature/pdf/legacy/PdfReaderScreen.kt`

## Why This Note Exists

- The parked PDF path still exists in source.
- The current shell boundary must not be re-enabled accidentally.
- Any future PDF revival should begin with a fresh design decision, not piecemeal reactivation.

## Safe Starting Point For Future Work

1. Re-check the active shell boundary in `AppNavigation.kt`.
2. Re-check parser/runtime seams in `EpubParser.kt` and `PdfLegacyBridge.kt`.
3. Decide whether PDF returns as a separate runtime surface or is removed entirely.
4. Re-enable the shell only after import, fallback, progress, and memory behavior are verified together.
