# EPUB Parsing Guide

Use this guide when the task touches import, metadata cache, TOC rebuild, chapter parsing, image resolution, or EPUB mutation for the Edit Book flow.

## Read Order

1. `data/parser/EpubParser.kt`
   - Public parser facade and call-site surface.
2. One focused helper file only if needed:
   - `EpubParserBooks.kt` for import, metadata, TOC, cover, or book ID work
   - `EpubParserEditing.kt` for EPUB mutation and staged writes
   - `EpubParserChapter.kt` for chapter text, images, malformed XHTML, or `normalizePath()`

## File Boundaries

- `EpubParser.kt`
  - Public API used by `AppNavigation` and `ReaderScreen`.
- `EpubParserBooks.kt`
  - `buildBookId(...)`, metadata cache, TOC reconstruction, and cover extraction.
- `EpubParserEditing.kt`
  - Title/author updates, cover replace/remove, chapter reorder/rename, and text/html insertion.
- `EpubParserChapter.kt`
  - Chapter parsing, ZIP entry lookup, image resolution, malformed XHTML cleanup, and `normalizePath()`.

## Do Not Change Accidentally

- Book IDs must remain `MD5(uri + fileSize)`.
- `ZipFile` and stream handling must stay wrapped in `.use {}`.
- `metadata.json` field names and overall shape must stay stable.
- Edit-book writes must stay staged and atomic. Never overwrite `book.epub` in place.
- `normalizePath()` must remain stable for `../`, `OEBPS/`, and `OPS/` layouts.
- Parser error tolerance for malformed XHTML must remain in place.

## Metadata And Repair Rules

- Import always rebuilds metadata.
- Edit-book save reparses the stored EPUB so `metadata.json`, cover cache, TOC, and spine stay aligned.
- Missing or corrupt `metadata.json` should fail safely instead of crashing the library scan.

## Performance Notes

- Keep chapter parsing on `Dispatchers.IO`.
- `ChapterElement.Image` is file-backed; do not switch it back to long-lived in-memory `ByteArray` payloads without a deliberate memory review.
- Invalidate parser caches whenever stored chapter content changes.
