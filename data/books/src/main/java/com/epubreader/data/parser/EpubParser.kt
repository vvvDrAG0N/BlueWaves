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
import com.epubreader.core.debug.AppLog
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.BookEditRequest
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.ConversionStatus
import com.epubreader.core.model.EpubBook
import com.epubreader.data.pdf.legacy.PdfLegacyRuntime
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException

/**
 * Handles all EPUB extraction and parsing logic.
 * AI_WARNING: All methods should ideally be called from Dispatchers.IO.
 */
class EpubParser internal constructor(
    private val context: Context,
    private val pdfLegacyBridge: PdfLegacyBridge = PdfLegacyRuntime(context),
) {
    private val chapterCacheLock = Any()
    private val chapterCache = object : LinkedHashMap<String, List<ChapterElement>>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ChapterElement>>?): Boolean {
            return size > 12
        }
    }

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
            val fileSize = queryFileSize(context, uri)
                ?: return ImportInspectionResult.Rejected(ImportFailureReason.ReadFailed)
            val displayName = queryDisplayName(context, uri)
            val mimeType = context.contentResolver.getType(uri)
            val headerBytes = readHeaderBytes(context, uri)
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
                return when (val archiveResult = inspectZipSource(context, uri)) {
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
        val bookFolder = File(booksDir, request.bookId)
        val folderExisted = bookFolder.exists()
        val stagedTargetFile = stagedStoredBookFile(bookFolder, request.format)
        val targetFile = storedBookFile(bookFolder, request.format)

        return try {
            if (!folderExisted) {
                bookFolder.mkdirs()
            }
            stagedTargetFile.delete()

            val copied = if (request.archiveEntryPath == null) {
                copyUriToFile(context, request.uri, stagedTargetFile)
            } else {
                extractArchiveEntry(context, request.uri, request.archiveEntryPath, stagedTargetFile)
            }

            if (!copied) {
                cleanupFailedImport(bookFolder, stagedTargetFile, folderExisted)
                return null
            }

            replaceFileAtomically(stagedTargetFile, targetFile)

            when (request.format) {
                BookFormat.EPUB -> {
                    cleanupArtifactsForNativeEpub(bookFolder, pdfLegacyBridge.workspaceDirName)
                    reparseBook(bookFolder)
                }
                BookFormat.PDF -> importPdfSource(bookFolder, request.displayName, pdfLegacyBridge.workspaceDirName)
            }
        } catch (error: Exception) {
            if (error is CancellationException) {
                throw error
            }
            cleanupFailedImport(bookFolder, stagedTargetFile, folderExisted)
            AppLog.e(AppLog.PARSER, error) { "Failed to import ${request.uri}" }
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
    fun reparseBook(bookFolder: File): EpubBook? {
        invalidateChapterCache(bookFolder.absolutePath)
        return rebuildBookMetadata(bookFolder)
    }

    fun editBook(
        book: EpubBook,
        request: BookEditRequest,
    ): EpubBook? {
        if (book.sourceFormat != BookFormat.EPUB) {
            return null
        }
        return editStoredEpubBook(File(book.rootPath), request)
    }

    fun scanBooks(): List<EpubBook> {
        return booksDir.listFiles()
            ?.filter(File::isDirectory)
            ?.mapNotNull { folder ->
                val cachedBook = loadBookMetadata(folder) ?: return@mapNotNull null
                val book = healCachedBook(folder, cachedBook) ?: return@mapNotNull null
                val storedFile = resolveStoredBookFile(book)
                if (!storedFile.exists()) {
                    AppLog.w(AppLog.PARSER) { "Skipping cached book ${folder.name} because ${storedFile.name} is missing" }
                    return@mapNotNull null
                }

                if (book.format == BookFormat.PDF && book.pageCount <= 0) {
                    AppLog.w(AppLog.PARSER) { "Rebuilding legacy PDF metadata for ${folder.name}" }
                    return@mapNotNull rebuildPdfMetadata(
                        folder,
                        displayName = null,
                        conversionStatus = book.conversionStatus,
                        preferredFormat = book.format,
                        conversionCompletedPages = book.conversionCompletedPages,
                        conversionTotalPages = book.conversionTotalPages,
                    )
                }

                book
            }
            ?: emptyList()
    }

    fun deleteBook(book: EpubBook) {
        // AI_MUTATION_POINT: Deletes files from disk.
        // AI_WARNING: Must trigger UI refresh after this.
        invalidateChapterCache(book.rootPath)
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
        val cacheKey = "$bookFolderPath::$href"
        synchronized(chapterCacheLock) {
            chapterCache[cacheKey]?.let { return it }
        }

        return parseBookChapter(bookFolderPath, href).also { elements ->
            synchronized(chapterCacheLock) {
                chapterCache[cacheKey] = elements
            }
        }
    }

    fun resolveStoredBookFile(book: EpubBook): File {
        return resolveStoredBookFile(book, book.activeRepresentation)
    }

    fun resolveStoredBookFile(
        book: EpubBook,
        representation: BookRepresentation,
    ): File {
        val bookFolder = File(book.rootPath)
        return when (representation) {
            BookRepresentation.EPUB -> activeEpubFile(bookFolder, book.sourceFormat)
            BookRepresentation.PDF -> ensureCanonicalSourcePdfFile(bookFolder) ?: File(bookFolder, PDF_DOCUMENT_FILE_NAME)
        }
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

    fun prepareBookForReading(book: EpubBook): EpubBook {
        val bookFolder = File(book.rootPath)
        val current = loadBookMetadata(bookFolder) ?: book
        val hasPdfFallback = ensureCanonicalSourcePdfFile(bookFolder) != null
        if (current.sourceFormat != BookFormat.PDF) {
            return current
        }

        if (current.conversionStatus == ConversionStatus.READY && !isGeneratedEpubReadyForReading(bookFolder, current)) {
            AppLog.w(AppLog.PARSER) { "Generated EPUB fallback failed for ${current.id}; demoting to raw PDF" }
            return rebuildPdfMetadata(
                bookFolder,
                displayName = null,
                conversionStatus = ConversionStatus.FAILED,
                preferredFormat = BookFormat.PDF,
                conversionCompletedPages = current.conversionCompletedPages,
                conversionTotalPages = current.conversionTotalPages,
            ) ?: current.copy(
                format = BookFormat.PDF,
                conversionStatus = ConversionStatus.FAILED,
                hasPdfFallback = hasPdfFallback,
            )
        }

        if (current.format != BookFormat.EPUB) {
            return current.copy(hasPdfFallback = hasPdfFallback)
        }

        return current
    }

    fun setBookRepresentation(
        book: EpubBook,
        representation: BookRepresentation,
    ): EpubBook? {
        val bookFolder = File(book.rootPath)
        val current = loadBookMetadata(bookFolder) ?: book
        return when (representation) {
            BookRepresentation.EPUB -> {
                if (!current.canOpenGeneratedEpub) {
                    null
                } else {
                    val updated = current.copy(format = BookFormat.EPUB)
                    if (!isGeneratedEpubReadyForReading(bookFolder, updated)) {
                        rebuildPdfMetadata(
                            bookFolder = bookFolder,
                            displayName = null,
                            conversionStatus = ConversionStatus.FAILED,
                            preferredFormat = BookFormat.PDF,
                            conversionCompletedPages = current.conversionCompletedPages,
                            conversionTotalPages = current.conversionTotalPages,
                        ) ?: current.copy(
                            format = BookFormat.PDF,
                            conversionStatus = ConversionStatus.FAILED,
                            hasPdfFallback = ensureCanonicalSourcePdfFile(bookFolder) != null,
                        )
                    } else {
                        saveBookMetadata(bookFolder, updated)
                        updated
                    }
                }
            }

            BookRepresentation.PDF -> {
                if (current.sourceFormat != BookFormat.PDF || ensureCanonicalSourcePdfFile(bookFolder) == null) {
                    null
                } else {
                    val updated = current.copy(format = BookFormat.PDF, hasPdfFallback = true)
                    saveBookMetadata(bookFolder, updated)
                    updated
                }
            }
        }
    }

    fun retryPdfConversion(book: EpubBook): EpubBook? {
        if (book.sourceFormat != BookFormat.PDF) {
            return null
        }
        val bookFolder = File(book.rootPath)
        val current = loadBookMetadata(bookFolder) ?: book
        val queued = rebuildPdfMetadata(
            bookFolder = bookFolder,
            displayName = null,
            conversionStatus = ConversionStatus.QUEUED,
            preferredFormat = BookFormat.PDF,
            conversionCompletedPages = current.conversionCompletedPages,
            conversionTotalPages = current.conversionTotalPages.takeIf { it > 0 } ?: current.pageCount,
        ) ?: return null
        pdfLegacyBridge.enqueue(queued.id)
        return queued
    }

    fun cancelPdfConversion(bookId: String) {
        pdfLegacyBridge.cancel(bookId)
    }

    internal suspend fun convertStoredPdfForBook(
        bookId: String,
        onProgress: PdfConversionProgressListener = PdfConversionProgressListener {},
    ): EpubBook? {
        return convertStoredPdfForBook(
            booksDir = booksDir,
            workspaceDirName = pdfLegacyBridge.workspaceDirName,
            pdfLegacyBridge = pdfLegacyBridge,
            bookId = bookId,
            onProgress = onProgress,
        )
    }

    private fun invalidateChapterCache(bookRootPath: String) {
        synchronized(chapterCacheLock) {
            val iterator = chapterCache.keys.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().startsWith("$bookRootPath::")) {
                    iterator.remove()
                }
            }
        }
    }

    companion object {
        fun create(context: Context): EpubParser = EpubParser(context.applicationContext)
    }
}
