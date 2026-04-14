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
import com.epubreader.core.debug.AppLog
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.ConversionStatus
import com.epubreader.core.model.EpubBook
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException

/**
 * Handles all EPUB extraction and parsing logic.
 * AI_WARNING: All methods should ideally be called from Dispatchers.IO.
 */
class EpubParser internal constructor(
    private val context: Context,
    private val pdfToEpubConverter: PdfToEpubConverter = MlKitPdfToEpubConverter(context),
) {

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

    internal fun inspectImportSource(uri: Uri): ImportInspectionResult {
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

    internal fun importBook(request: ImportRequest): EpubBook? {
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
                copyUriToFile(request.uri, stagedTargetFile)
            } else {
                extractArchiveEntry(request.uri, request.archiveEntryPath, stagedTargetFile)
            }

            if (!copied) {
                cleanupFailedImport(bookFolder, stagedTargetFile, folderExisted)
                return null
            }

            replaceFileAtomically(stagedTargetFile, targetFile)

            when (request.format) {
                BookFormat.EPUB -> {
                    cleanupArtifactsForNativeEpub(bookFolder)
                    reparseBook(bookFolder)
                }
                BookFormat.PDF -> importPdfSource(bookFolder, request.displayName)
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
    fun reparseBook(bookFolder: File): EpubBook? = rebuildBookMetadata(bookFolder)

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
        if (current.sourceFormat != BookFormat.PDF) {
            return current
        }

        if (current.format != BookFormat.EPUB) {
            return current.copy(hasPdfFallback = ensureCanonicalSourcePdfFile(bookFolder) != null)
        }

        val generatedEpub = activeEpubFile(bookFolder, BookFormat.PDF)
        if (!generatedEpub.exists() || !validateGeneratedEpub(generatedEpub)) {
            AppLog.w(AppLog.PARSER) { "Generated EPUB fallback failed for ${current.id}; demoting to raw PDF" }
            return rebuildPdfMetadata(
                bookFolder,
                displayName = null,
                conversionStatus = ConversionStatus.FAILED,
                preferredFormat = BookFormat.PDF,
                conversionCompletedPages = current.conversionCompletedPages,
                conversionTotalPages = current.conversionTotalPages,
            ) ?: current
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
                    saveBookMetadata(bookFolder, updated)
                    updated
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
        enqueuePdfConversionWork(context, queued.id)
        return queued
    }

    fun cancelPdfConversion(bookId: String) {
        cancelPdfConversionWork(context, bookId)
    }

    internal suspend fun convertStoredPdfForBook(
        bookId: String,
        onProgress: PdfConversionProgressListener = PdfConversionProgressListener {},
    ): EpubBook? {
        val bookFolder = File(booksDir, bookId)
        if (!bookFolder.exists()) {
            return null
        }

        val existing = loadBookMetadata(bookFolder)
            ?: rebuildPdfMetadata(
                bookFolder = bookFolder,
                displayName = null,
                conversionStatus = ConversionStatus.NONE,
            )
            ?: return null
        if (existing.sourceFormat != BookFormat.PDF) {
            return existing
        }

        val generatedEpub = activeEpubFile(bookFolder, BookFormat.PDF)
        if (generatedEpub.exists() && validateGeneratedEpub(generatedEpub)) {
            return rebuildBookMetadata(bookFolder) ?: existing
        }

        val sourcePdf = ensureCanonicalSourcePdfFile(bookFolder) ?: return null
        val workspaceDir = File(bookFolder, PDF_CONVERSION_WORKSPACE_DIR_NAME)
        val stagedGeneratedEpub = File(bookFolder, "$GENERATED_EPUB_FILE_NAME.importing")
        stagedGeneratedEpub.delete()
        generatedEpub.takeIf { it.exists() && !validateGeneratedEpub(it) }?.delete()

        val runningBook = rebuildPdfMetadata(
            bookFolder = bookFolder,
            displayName = null,
            conversionStatus = ConversionStatus.RUNNING,
            preferredFormat = BookFormat.PDF,
            conversionCompletedPages = existing.conversionCompletedPages,
            conversionTotalPages = existing.conversionTotalPages.takeIf { it > 0 } ?: existing.pageCount,
        ) ?: existing
        onProgress.onProgress(
            PdfConversionProgress(
                completedPages = runningBook.conversionCompletedPages,
                totalPages = runningBook.conversionTotalPages.takeIf { it > 0 } ?: runningBook.pageCount,
            ),
        )

        try {
            val conversionResult = pdfToEpubConverter.convert(
                pdfFile = sourcePdf,
                workspaceDir = workspaceDir,
                outputFile = stagedGeneratedEpub,
                title = runningBook.title,
                author = runningBook.author,
                onProgress = PdfConversionProgressListener { progress ->
                    rebuildPdfMetadata(
                        bookFolder = bookFolder,
                        displayName = null,
                        conversionStatus = ConversionStatus.RUNNING,
                        preferredFormat = BookFormat.PDF,
                        conversionCompletedPages = progress.completedPages,
                        conversionTotalPages = progress.totalPages,
                    )
                    onProgress.onProgress(progress)
                },
            )

            if (conversionResult.succeeded && stagedGeneratedEpub.exists() && validateGeneratedEpub(stagedGeneratedEpub)) {
                replaceFileAtomically(stagedGeneratedEpub, generatedEpub)
                workspaceDir.deleteRecursively()
                return rebuildBookMetadata(bookFolder)
            }

            stagedGeneratedEpub.takeIf(File::exists)?.delete()
            generatedEpub.takeIf(File::exists)?.delete()
            workspaceDir.deleteRecursively()
            return rebuildPdfMetadata(
                bookFolder = bookFolder,
                displayName = null,
                conversionStatus = ConversionStatus.FAILED,
                preferredFormat = BookFormat.PDF,
                conversionCompletedPages = conversionResult.completedPages,
                conversionTotalPages = conversionResult.totalPages.takeIf { it > 0 } ?: existing.pageCount,
            )
        } catch (error: Exception) {
            if (error is CancellationException) {
                throw error
            }
            AppLog.w(AppLog.PARSER, error) { "Stored PDF conversion failed for $bookId" }
            stagedGeneratedEpub.takeIf(File::exists)?.delete()
            generatedEpub.takeIf { it.exists() && !validateGeneratedEpub(it) }?.delete()
            workspaceDir.deleteRecursively()
            return rebuildPdfMetadata(
                bookFolder = bookFolder,
                displayName = null,
                conversionStatus = ConversionStatus.FAILED,
                preferredFormat = BookFormat.PDF,
                conversionCompletedPages = runningBook.conversionCompletedPages,
                conversionTotalPages = runningBook.conversionTotalPages.takeIf { it > 0 } ?: runningBook.pageCount,
            )
        }
    }

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

    private fun stagedStoredBookFile(bookFolder: File, format: BookFormat): File {
        return File(bookFolder, "${storedFileName(format)}.importing")
    }

    private fun cleanupFailedImport(bookFolder: File, stagedTargetFile: File, folderExisted: Boolean) {
        if (stagedTargetFile.exists()) {
            stagedTargetFile.delete()
        }
        if (!folderExisted && bookFolder.exists()) {
            bookFolder.deleteRecursively()
        }
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

    private fun importPdfSource(
        bookFolder: File,
        displayName: String?,
    ): EpubBook? {
        cleanupArtifactsForPdfImport(bookFolder)

        val queuedPdfBook = rebuildPdfMetadata(
            bookFolder,
            displayName = displayName,
            conversionStatus = ConversionStatus.QUEUED,
            preferredFormat = BookFormat.PDF,
            conversionCompletedPages = 0,
            conversionTotalPages = 0,
        ) ?: return null

        val sourcePdf = ensureCanonicalSourcePdfFile(bookFolder) ?: return queuedPdfBook
        File(bookFolder, LEGACY_PDF_DOCUMENT_FILE_NAME)
            .takeIf { it.exists() && it.absolutePath != sourcePdf.absolutePath }
            ?.delete()
        return queuedPdfBook
    }

    private fun healCachedBook(
        bookFolder: File,
        book: EpubBook,
    ): EpubBook? {
        if (book.sourceFormat != BookFormat.PDF) {
            return book
        }

        val generatedEpub = activeEpubFile(bookFolder, BookFormat.PDF)
        return when {
            book.format == BookFormat.EPUB && !generatedEpub.exists() -> {
                rebuildPdfMetadata(
                    bookFolder,
                    displayName = null,
                    conversionStatus = ConversionStatus.FAILED,
                    preferredFormat = BookFormat.PDF,
                    conversionCompletedPages = book.conversionCompletedPages,
                    conversionTotalPages = book.conversionTotalPages,
                )
            }

            book.format == BookFormat.EPUB && book.spineHrefs.isEmpty() -> {
                rebuildBookMetadata(bookFolder)
                    ?: rebuildPdfMetadata(
                        bookFolder,
                        displayName = null,
                        conversionStatus = ConversionStatus.FAILED,
                        preferredFormat = BookFormat.PDF,
                        conversionCompletedPages = book.conversionCompletedPages,
                        conversionTotalPages = book.conversionTotalPages,
                    )
            }

            else -> book.copy(
                hasPdfFallback = ensureCanonicalSourcePdfFile(bookFolder) != null,
                conversionTotalPages = book.conversionTotalPages.takeIf { it > 0 } ?: book.pageCount,
            )
        }
    }

    private fun cleanupArtifactsForNativeEpub(bookFolder: File) {
        listOf(
            File(bookFolder, GENERATED_EPUB_FILE_NAME),
            File(bookFolder, PDF_DOCUMENT_FILE_NAME),
            File(bookFolder, LEGACY_PDF_DOCUMENT_FILE_NAME),
            File(bookFolder, "$GENERATED_EPUB_FILE_NAME.importing"),
        ).filter(File::exists).forEach(File::delete)
        File(bookFolder, PDF_CONVERSION_WORKSPACE_DIR_NAME)
            .takeIf(File::exists)
            ?.deleteRecursively()
    }

    private fun cleanupArtifactsForPdfImport(bookFolder: File) {
        File(bookFolder, EPUB_ARCHIVE_FILE_NAME)
            .takeIf(File::exists)
            ?.delete()
        File(bookFolder, GENERATED_EPUB_FILE_NAME)
            .takeIf(File::exists)
            ?.delete()
        File(bookFolder, "$GENERATED_EPUB_FILE_NAME.importing")
            .takeIf(File::exists)
            ?.delete()
        File(bookFolder, PDF_CONVERSION_WORKSPACE_DIR_NAME)
            .takeIf(File::exists)
            ?.deleteRecursively()
    }

    private fun validateGeneratedEpub(epubFile: File): Boolean {
        return runCatching {
            epubFile.inputStream().use { input ->
                val book = nl.siegmann.epublib.epub.EpubReader().readEpub(input)
                book.spine.spineReferences.isNotEmpty()
            }
        }.getOrDefault(false)
    }
}
