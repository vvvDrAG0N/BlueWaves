/**
 * AI_ENTRY_POINT
 * AI_READ_FIRST
 * AI_RELEVANT_TO: [EPUB Parsing, Book Extraction, Chapter Rendering Data, Metadata Management]
 * AI_STATE_OWNER: Stateless facade - callers manage lifecycle.
 * AI_DATA_FLOW: Uri -> parseAndExtract() -> cache/books -> EpubBook
 * AI_WARNING: All parsing is IO-heavy; call from Dispatchers.IO.
 *
 * FILE: EpubParser.kt
 * PURPOSE: Public EPUB parser facade.
 * RESPONSIBILITIES:
 *  - Owns the public parser API used by app shell and reader code.
 *  - Coordinates import, cached book scans, chapter parsing, and metadata updates.
 *  - Delegates chapter parsing and metadata/book rebuild details to package-local helpers.
 * NON-GOALS:
 *  - Does not own UI logic.
 *  - Does not own DataStore-backed state.
 */
package com.epubreader.data.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.EpubBook
import java.io.File
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Handles all EPUB extraction and parsing logic.
 * AI_WARNING: All methods should ideally be called from Dispatchers.IO.
 */
class EpubParser(private val context: Context) {

    private val booksDir = File(context.cacheDir, EPUB_BOOKS_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    /**
     * AI_MUTATION_POINT
     * AI_WARNING: Must trigger UI refresh after this.
     *
     * PURPOSE: Imports an EPUB from a Uri, extracts it to internal storage, and generates metadata.
     * INPUT: Uri of the EPUB file.
     * OUTPUT: [EpubBook] object if successful, null otherwise.
     * SIDE EFFECTS: Writes to cache/books directory.
     * AI_WARNING: This duplicates the entire EPUB file into internal storage.
     */
    fun parseAndExtract(uri: Uri): EpubBook? {
        return when (val result = inspectImportSource(uri)) {
            is ImportInspectionResult.Ready -> importBook(result.request)
            is ImportInspectionResult.Rejected -> null
        }
    }

    fun inspectImportSource(uri: Uri): ImportInspectionResult {
        return try {
            val fileSize = queryFileSize(uri) ?: return ImportInspectionResult.Rejected(ImportFailureReason.ReadFailed)
            val displayName = queryDisplayName(uri)
            val mimeType = context.contentResolver.getType(uri)
            val headerBytes = readHeaderBytes(uri)
            val bookId = buildBookId(uri, fileSize)
            val normalizedName = displayName?.lowercase().orEmpty()
            val normalizedMime = mimeType?.lowercase().orEmpty()
            val shouldInspectAsZip = isZipSignature(headerBytes) ||
                normalizedName.endsWith(".epub") ||
                normalizedName.endsWith(".zip") ||
                normalizedMime == EPUB_MIME_TYPE ||
                normalizedMime == ZIP_MIME_TYPE ||
                normalizedMime == ZIP_COMPRESSED_MIME_TYPE ||
                normalizedMime == OCTET_STREAM_MIME_TYPE

            if (shouldInspectAsZip) {
                return when (val archiveResult = inspectZipSource(uri)) {
                    ArchiveInspectionResult.DirectEpubContainer -> {
                        ImportInspectionResult.Ready(
                            ImportRequest(
                                bookId = bookId,
                                uri = uri,
                                format = BookFormat.EPUB,
                                displayName = displayName,
                            ),
                        )
                    }

                    is ArchiveInspectionResult.Candidate -> {
                        ImportInspectionResult.Ready(
                            ImportRequest(
                                bookId = bookId,
                                uri = uri,
                                format = archiveResult.candidate.format,
                                archiveEntryPath = archiveResult.candidate.entryPath,
                                displayName = archiveResult.candidate.entryPath.substringAfterLast('/'),
                            ),
                        )
                    }

                    is ArchiveInspectionResult.Rejected -> {
                        val fallbackFormat = inferDirectImportFormat(
                            fileName = displayName,
                            mimeType = mimeType,
                            headerBytes = headerBytes,
                            looksLikeEpubContainer = false,
                        )
                        if (fallbackFormat != null && archiveResult.reason == ImportFailureReason.ReadFailed) {
                            ImportInspectionResult.Ready(
                                ImportRequest(
                                    bookId = bookId,
                                    uri = uri,
                                    format = fallbackFormat,
                                    displayName = displayName,
                                ),
                            )
                        } else {
                            ImportInspectionResult.Rejected(archiveResult.reason)
                        }
                    }
                }
            }

            val directFormat = inferDirectImportFormat(
                fileName = displayName,
                mimeType = mimeType,
                headerBytes = headerBytes,
                looksLikeEpubContainer = false,
            )

            if (directFormat != null) {
                ImportInspectionResult.Ready(
                    ImportRequest(
                        bookId = bookId,
                        uri = uri,
                        format = directFormat,
                        displayName = displayName,
                    ),
                )
            } else {
                ImportInspectionResult.Rejected(ImportFailureReason.UnsupportedFileType)
            }
        } catch (error: Exception) {
            error.printStackTrace()
            ImportInspectionResult.Rejected(ImportFailureReason.ReadFailed)
        }
    }

    fun importBook(request: ImportRequest): EpubBook? {
        return try {
            val bookFolder = File(booksDir, request.bookId).apply {
                if (!exists()) mkdirs()
            }
            val targetFile = storedBookFile(bookFolder, request.format)
            cleanupOtherStoredFiles(bookFolder, request.format)

            val copied = if (request.archiveEntryPath == null) {
                copyUriToFile(request.uri, targetFile)
            } else {
                extractArchiveEntry(request.uri, request.archiveEntryPath, targetFile)
            }

            if (!copied) {
                return null
            }

            when (request.format) {
                BookFormat.EPUB -> reparseBook(bookFolder)
                BookFormat.PDF -> rebuildPdfMetadata(bookFolder, request.displayName)
            }
        } catch (error: Exception) {
            error.printStackTrace()
            null
        }
    }

    /**
     * AI_MUTATION_POINT
     * AI_WARNING: Must trigger UI refresh after this.
     *
     * PURPOSE: Fully parses an extracted EPUB folder into an [EpubBook] model.
     * INPUT: Folder where "book.epub" is stored.
     * OUTPUT: [EpubBook] object.
     * SIDE EFFECTS: Re-writes "metadata.json" in the folder.
     */
    fun reparseBook(bookFolder: File): EpubBook? = rebuildBookMetadata(bookFolder)

    fun scanBooks(): List<EpubBook> {
        return booksDir.listFiles()?.filter { it.isDirectory }?.mapNotNull(::loadBookMetadata) ?: emptyList()
    }

    fun deleteBook(book: EpubBook) {
        // AI_MUTATION_POINT: Deletes files from disk.
        // AI_WARNING: Must trigger UI refresh after this.
        val folder = File(book.rootPath)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
    }

    fun getChapterHref(book: EpubBook, index: Int): String? {
        if (book.format != BookFormat.EPUB) {
            return null
        }
        return book.spineHrefs.getOrNull(index)
    }

    /**
     * PURPOSE: Parses a specific chapter (XHTML file) within the EPUB.
     * INPUT: bookFolderPath, href of the chapter.
     * OUTPUT: List of [ChapterElement] (Text and Image nodes).
     * AI_CRITICAL: This delegates to the package-local chapter parser that preserves ZipFile safety,
     * relaxed XmlPullParser handling, and normalizePath() behavior.
     */
    fun parseChapter(bookFolderPath: String, href: String): List<ChapterElement> {
        return parseBookChapter(bookFolderPath, href)
    }

    fun resolveStoredBookFile(book: EpubBook): File {
        return storedBookFile(File(book.rootPath), book.format)
    }

    fun updateLastRead(book: EpubBook) {
        // AI_MUTATION_POINT: Updates progress in metadata.json.
        // AI_WARNING: Must trigger UI refresh after this.
        val folder = File(booksDir, book.id)
        if (folder.exists()) {
            saveBookMetadata(folder, book)
        }
    }

    /**
     * PURPOSE: Reads cached metadata.json for an already-imported book folder.
     * AI_NOTE: Kept public for compatibility with existing parser surface, even though current callers
     * mainly go through scanBooks().
     */
    fun loadMetadata(folder: File): EpubBook? = loadBookMetadata(folder)

    private fun queryFileSize(uri: Uri): Long? {
        return context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.statSize
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex != -1) {
                    return cursor.getString(columnIndex)
                }
            }
        }

        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun readHeaderBytes(uri: Uri, size: Int = 8): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            ByteArray(size).also { buffer ->
                val read = input.read(buffer)
                if (read > 0) {
                    return@use buffer.copyOf(read)
                }
            }
        } ?: ByteArray(0)
    }

    private fun inspectZipSource(uri: Uri): ArchiveInspectionResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zipInput ->
                    val entryNames = mutableListOf<String>()
                    var looksLikeEpubContainer = false
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.orEmpty()
                        entryNames += entryName
                        if (entryName.equals("META-INF/container.xml", ignoreCase = true)) {
                            looksLikeEpubContainer = true
                        } else if (entryName == "mimetype") {
                            val mimetype = String(zipInput.readBytes(), Charsets.UTF_8).trim()
                            if (mimetype == EPUB_MIME_TYPE) {
                                looksLikeEpubContainer = true
                            }
                        }
                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                    inspectArchiveEntries(entryNames, looksLikeEpubContainer)
                }
            } ?: ArchiveInspectionResult.Rejected(ImportFailureReason.ReadFailed)
        } catch (_: Exception) {
            ArchiveInspectionResult.Rejected(ImportFailureReason.ReadFailed)
        }
    }

    private fun copyUriToFile(uri: Uri, targetFile: File): Boolean {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
            true
        } ?: false
    }

    private fun extractArchiveEntry(
        uri: Uri,
        entryPath: String,
        targetFile: File,
    ): Boolean {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zipInput ->
                var entry = zipInput.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == entryPath) {
                        FileOutputStream(targetFile).use { output ->
                            zipInput.copyTo(output)
                        }
                        zipInput.closeEntry()
                        return@use true
                    }
                    zipInput.closeEntry()
                    entry = zipInput.nextEntry
                }
                false
            }
        } ?: false
    }

    private fun cleanupOtherStoredFiles(bookFolder: File, format: BookFormat) {
        val currentFileName = storedFileName(format)
        listOf(EPUB_ARCHIVE_FILE_NAME, PDF_DOCUMENT_FILE_NAME)
            .filter { it != currentFileName }
            .map { File(bookFolder, it) }
            .filter(File::exists)
            .forEach(File::delete)
    }

    private fun storedBookFile(bookFolder: File, format: BookFormat): File {
        return File(bookFolder, storedFileName(format))
    }

    private fun storedFileName(format: BookFormat): String {
        return when (format) {
            BookFormat.EPUB -> EPUB_ARCHIVE_FILE_NAME
            BookFormat.PDF -> PDF_DOCUMENT_FILE_NAME
        }
    }
}
