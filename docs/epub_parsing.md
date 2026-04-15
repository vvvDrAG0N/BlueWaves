# EPUB Parsing Note

This note is the low-context guide for the parser package after the safe split of `EpubParser.kt`.

## Read Order

1. `data/parser/EpubParser.kt`
   - Public facade and fastest way to understand the parser surface.
   - Start here for call sites, public methods, and high-level behavior.
2. `data/parser/EpubParserBooks.kt`
   - Load only for import, cached metadata, TOC rebuild, cover extraction, or book ID questions.
3. `data/parser/EpubParserChapter.kt`
   - Load only for chapter rendering, image resolution, malformed XHTML handling, or `normalizePath()` work.

## File Boundaries

- `EpubParser.kt`
  - Public API used by `AppNavigation` and `ReaderScreen`
  - Delegates import, metadata, and chapter details to package-local helpers
  - Still fronts the parser-side bridge to deprecated PDF internals retained for the upcoming safe refactor

- `EpubParserBooks.kt`
  - `buildBookId(uri, fileSize)`
  - `rebuildBookMetadata(...)`
  - `saveBookMetadata(...)`
  - `loadBookMetadata(...)`
  - TOC and cover rebuild helpers

- `EpubParserChapter.kt`
  - `parseBookChapter(...)`
  - ZIP chapter entry resolution
  - image entry resolution
  - `normalizePath(...)`
  - malformed XHTML cleanup and relaxed `XmlPullParser` loop

## Metadata Self-Healing

The parser is designed to be resilient against corrupted or missing `metadata.json` files and malformed EPUB binaries.

### When is metadata.json considered invalid?
- If the file does not exist in the book's folder.
- If the JSON is malformed (triggers a catch block returning `null`).
- If core fields (like `id` or `toc`) are missing or have incorrect types.

### What triggers `rebuildBookMetadata`?
- **Initial Import:** Always called during `parseAndExtract`.
- **Manual Reparse:** Can be triggered via `EpubParser.reparseBook(folder)` (common in tests or if a user triggers a library refresh that detects a hash mismatch, though current logic primarily relies on `loadBookMetadata` during scans).
- **Corrupt Cache:** If `loadBookMetadata` returns `null` during a scan, the book will not appear in the library until it is re-imported or manually repaired.

### Auto-fixed Fields & Resilience Logic
- **Title/Author:** If missing in the EPUB binary, defaults to "Unknown Title" and "Unknown Author".
- **Table of Contents (TOC):** 
  - If the EPUB's TOC is empty or missing, the parser reconstructs it using the file names from the spine (e.g., `chapter_1.xhtml` becomes "1. Chapter 1").
  - TOC titles have a multi-step fallback: `ref.title` -> `ref.resource.title` -> filename -> "Chapter".
- **Date Added:** If missing in `metadata.json`, it falls back to the filesystem's `lastModified` timestamp of the book folder.
- **Cover Path:** Sanitized to handle `null` strings or empty paths stored in JSON.
- **Chapter Navigation:** If a TOC entry has no `completeHref`, it falls back to the base `resource.href`.

## Do Not Change Accidentally

- Book IDs must remain `MD5(uri + fileSize)`.
- `ZipFile` and `InputStream` handling must stay wrapped in `.use {}`.
- `normalizePath()` behavior must remain stable for `../`, `OEBPS/`, and `OPS/` layouts.
- `metadata.json` field names and shape must not drift during safe refactors.
- Chapter parser error tolerance must remain in place for malformed EPUB XHTML.

## AI Hint

Do not load all three parser files by default.

- For duplicate/import/cache bugs: start with `EpubParser.kt`, then `EpubParserBooks.kt`.
- For broken chapter text or images: start with `EpubParser.kt`, then `EpubParserChapter.kt`.
- For PDF-related tasks, assume the shell is intentionally blocking active PDF support and only load `data/parser/PdfLegacyBridge.kt` plus `data/pdf/legacy/*` if the task is explicitly about the parked runtime or the upcoming refactor.
