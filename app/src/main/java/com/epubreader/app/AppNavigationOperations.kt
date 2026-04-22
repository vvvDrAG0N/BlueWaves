/**
 * AI_READ_AFTER: AppNavigation.kt
 * AI_RELEVANT_TO: [Library Import, Last-Read Updates, Folder/Book Mutations]
 * PURPOSE: Package-local side-effect helpers for the app shell.
 * AI_WARNING: Persistence still belongs to `SettingsManager`; file work still belongs to `EpubParser`.
 */
package com.epubreader.app

import android.content.Context
import android.net.Uri
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.BookEditRequest
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.EpubBook
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

private const val PdfImportDisabledMessage =
    "PDF support is temporarily disabled while we prepare a safer refactor."

internal sealed interface ImportBookResult {
    data class Duplicate(val folderName: String) : ImportBookResult
    data class Imported(val book: EpubBook) : ImportBookResult
    data class Failed(val message: String) : ImportBookResult
}

internal sealed interface EditBookResult {
    data class Updated(val book: EpubBook) : EditBookResult
    data class Failed(val message: String) : EditBookResult
}

internal suspend fun scanLibrary(parser: EpubParser): List<EpubBook> {
    return withContext(Dispatchers.IO) {
        parser.scanBooks()
    }
}

internal suspend fun touchBookLastRead(
    parser: EpubParser,
    book: EpubBook,
): EpubBook {
    val updated = book.copy(lastRead = System.currentTimeMillis())
    withContext(Dispatchers.IO) {
        parser.updateLastRead(updated)
    }
    return updated
}

internal suspend fun importBookIntoLibrary(
    books: List<EpubBook>,
    context: Context,
    uri: Uri,
    parser: EpubParser,
    settingsManager: SettingsManager,
    selectedFolderName: String,
    bookGroups: Map<String, String>,
): ImportBookResult {
    return try {
        val existingBook = withContext(Dispatchers.IO) {
            findExistingBook(books = books, context = context, uri = uri)
        }
        if (existingBook != null) {
            val folderName = bookGroups[existingBook.id].orEmpty().ifBlank { RootLibraryName }
            return ImportBookResult.Duplicate(folderName)
        }

        val request = when (val inspection = withContext(Dispatchers.IO) { parser.inspectImportSource(uri) }) {
            is com.epubreader.data.parser.ImportInspectionResult.Ready -> inspection.request
            is com.epubreader.data.parser.ImportInspectionResult.Rejected -> {
                return ImportBookResult.Failed(inspection.reason.userMessage)
            }
        }
        if (request.format == BookFormat.PDF) {
            return ImportBookResult.Failed(PdfImportDisabledMessage)
        }

        val newBook = withContext(Dispatchers.IO) {
            parser.importBook(request)
        } ?: return ImportBookResult.Failed("Couldn't import this book.")

        settingsManager.updateBookGroup(
            newBook.id,
            if (selectedFolderName == RootLibraryName) null else selectedFolderName,
        )
        ImportBookResult.Imported(newBook)
    } catch (error: Exception) {
        if (error is CancellationException) {
            throw error
        }
        ImportBookResult.Failed("Couldn't import this book.")
    }
}

internal suspend fun moveBooksToFolder(
    settingsManager: SettingsManager,
    bookIds: Set<String>,
    folderName: String,
) {
    settingsManager.updateBookGroups(
        bookIds = bookIds,
        groupName = if (folderName == RootLibraryName) null else folderName,
    )
}

internal suspend fun deleteSelectedBooks(
    parser: EpubParser,
    settingsManager: SettingsManager,
    books: List<EpubBook>,
    selectedBookIds: Set<String>,
) {
    val idsToDelete = selectedBookIds.toList()
    withContext(Dispatchers.IO) {
        idsToDelete.forEach { id ->
            parser.cancelPdfConversion(id)
            books.find { it.id == id }?.let { parser.deleteBook(it) }
        }
    }
    settingsManager.deleteBooksData(idsToDelete.toSet())
}

internal suspend fun editBookInLibrary(
    parser: EpubParser,
    settingsManager: SettingsManager,
    book: EpubBook,
    request: BookEditRequest,
): EditBookResult {
    if (book.sourceFormat != BookFormat.EPUB) {
        return EditBookResult.Failed("Only EPUB books can be edited right now.")
    }

    return try {
        val existingProgress = settingsManager
            .getBookProgress(book.id, BookRepresentation.EPUB)
            .first()
        val updatedBook = withContext(Dispatchers.IO) {
            parser.editBook(book, request)
        } ?: return EditBookResult.Failed("Couldn't save changes to this book.")

        val repairedProgress = repairProgressAfterBookEdit(
            previousProgress = existingProgress,
            originalSpineHrefs = book.spineHrefs,
            updatedSpineHrefs = updatedBook.spineHrefs,
        )
        settingsManager.saveBookProgress(
            bookId = book.id,
            progress = repairedProgress,
            representation = BookRepresentation.EPUB,
        )
        EditBookResult.Updated(updatedBook)
    } catch (error: Exception) {
        if (error is CancellationException) {
            throw error
        }
        EditBookResult.Failed("Couldn't save changes to this book.")
    }
}

private fun findExistingBook(books: List<EpubBook>, context: Context, uri: Uri): EpubBook? {
    val fileSize = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    } catch (_: Exception) {
        return null
    }

    val rawId = "$uri$fileSize"
    val bookId = MessageDigest.getInstance("MD5")
        .digest(rawId.toByteArray())
        .joinToString("") { "%02x".format(it) }

    return books.find { it.id == bookId }
}

internal fun repairProgressAfterBookEdit(
    previousProgress: BookProgress,
    originalSpineHrefs: List<String>,
    updatedSpineHrefs: List<String>,
): BookProgress {
    val updatedHrefs = updatedSpineHrefs
        .map(::normalizeProgressHref)
        .filter(String::isNotBlank)
    if (updatedHrefs.isEmpty()) {
        return BookProgress()
    }

    val previousHref = normalizeProgressHref(previousProgress.lastChapterHref)
    if (previousHref.isBlank()) {
        return previousProgress
    }

    val existingMatch = updatedHrefs.firstOrNull { it == previousHref }
    if (existingMatch != null) {
        return previousProgress.copy(lastChapterHref = existingMatch)
    }

    val originalHrefs = originalSpineHrefs
        .map(::normalizeProgressHref)
        .filter(String::isNotBlank)
    val originalIndex = originalHrefs.indexOf(previousHref)

    if (originalIndex != -1) {
        for (index in originalIndex until originalHrefs.size) {
            val candidate = originalHrefs[index]
            if (candidate in updatedHrefs) {
                return BookProgress(lastChapterHref = candidate)
            }
        }
        for (index in (originalIndex - 1) downTo 0) {
            val candidate = originalHrefs[index]
            if (candidate in updatedHrefs) {
                return BookProgress(lastChapterHref = candidate)
            }
        }
    }

    return BookProgress(lastChapterHref = updatedHrefs.first())
}

private fun normalizeProgressHref(rawHref: String?): String {
    return rawHref
        .orEmpty()
        .substringBefore("#")
        .removePrefix("/")
        .trim()
}
