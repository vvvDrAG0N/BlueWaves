# Deprecated PDF Path

This document is now an archive note, not a statement of active product behavior.

## Current Status

- Active app-shell support is EPUB-only.
- `AppNavigation` blocks opening `sourceFormat == PDF` books and rejects new PDF imports.
- Existing PDF-origin library entries may still appear in scans so they can be preserved for the upcoming safe refactor.

## Parked Runtime Files

- `app/src/main/java/com/epubreader/feature/pdf/legacy/PdfReaderScreen.kt`
- `app/src/main/java/com/epubreader/data/pdf/legacy/PdfToEpubConverter.kt`
- `app/src/main/java/com/epubreader/data/pdf/legacy/PdfConversionWorker.kt`
- `app/src/main/java/com/epubreader/data/parser/PdfLegacyBridge.kt`
- `app/src/main/java/com/epubreader/app/AppNavigationPdfLegacy.kt`

These files remain in source as deprecated internals. They are intentionally not part of the active shell flow right now.

## Why The Feature Was Parked

The previous PDF path accumulated enough correctness and stability risk that a narrow bug-by-bug patching strategy stopped being worth it. The main risk clusters were:

- generated EPUB readiness and fallback correctness
- representation-switch progress persistence
- background conversion state freshness
- raw PDF rendering memory pressure

## Safe-Refactor Starting Point

When PDF support is revisited, start from these files first:

1. `app/src/main/java/com/epubreader/app/AppNavigation.kt`
2. `app/src/main/java/com/epubreader/app/AppNavigationPdfLegacy.kt`
3. `app/src/main/java/com/epubreader/app/AppNavigationOperations.kt`
4. `app/src/main/java/com/epubreader/data/parser/EpubParser.kt`
5. `app/src/main/java/com/epubreader/data/pdf/legacy/PdfToEpubConverter.kt`
6. `app/src/main/java/com/epubreader/feature/pdf/legacy/PdfReaderScreen.kt`

Expected direction for the refactor:

- keep `SettingsManager` as the persisted source of truth
- preserve EPUB reader behavior and restoration invariants
- decide whether PDF should return as a separate runtime surface or be removed entirely
- re-enable the shell only after import, fallback, progress, and memory behavior are proven together
