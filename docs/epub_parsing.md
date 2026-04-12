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
