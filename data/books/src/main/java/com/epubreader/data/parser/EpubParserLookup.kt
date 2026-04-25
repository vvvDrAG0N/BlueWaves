package com.epubreader.data.parser

import com.epubreader.core.debug.AppLog
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.EpubBook
import java.io.File

internal fun loadStoredBookById(
    booksDir: File,
    bookId: String,
): EpubBook? {
    val bookFolder = File(booksDir, bookId)
    if (!bookFolder.isDirectory) {
        return null
    }

    val cachedBook = loadBookMetadata(bookFolder)
        ?: rebuildStoredBookMetadata(bookFolder)
        ?: return null
    val healedBook = healCachedBook(bookFolder, cachedBook) ?: return null
    val storedFile = resolveStoredBookFile(bookFolder, healedBook)
    if (!storedFile.exists()) {
        AppLog.w(AppLog.PARSER) {
            "Skipping cached book ${bookFolder.name} because ${storedFile.name} is missing"
        }
        return null
    }

    if (healedBook.format == BookFormat.PDF && healedBook.pageCount <= 0) {
        AppLog.w(AppLog.PARSER) { "Rebuilding legacy PDF metadata for ${bookFolder.name}" }
        return rebuildPdfMetadata(
            bookFolder = bookFolder,
            displayName = null,
            conversionStatus = healedBook.conversionStatus,
            preferredFormat = healedBook.format,
            conversionCompletedPages = healedBook.conversionCompletedPages,
            conversionTotalPages = healedBook.conversionTotalPages,
        )
    }

    return healedBook
}

private fun rebuildStoredBookMetadata(bookFolder: File): EpubBook? {
    return when {
        ensureCanonicalSourcePdfFile(bookFolder) != null -> rebuildPdfMetadata(
            bookFolder = bookFolder,
            displayName = null,
        )

        activeEpubFile(bookFolder, BookFormat.EPUB).exists() -> rebuildBookMetadata(bookFolder)
        else -> null
    }
}

private fun resolveStoredBookFile(
    bookFolder: File,
    book: EpubBook,
): File {
    return when (book.activeRepresentation) {
        BookRepresentation.EPUB -> activeEpubFile(bookFolder, book.sourceFormat)
        BookRepresentation.PDF -> ensureCanonicalSourcePdfFile(bookFolder) ?: File(bookFolder, PDF_DOCUMENT_FILE_NAME)
    }
}
