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
import com.epubreader.core.model.EpubBook
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

internal sealed interface ImportBookResult {
    data class Duplicate(val folderName: String) : ImportBookResult
    data object Imported : ImportBookResult
    data class Failed(val message: String) : ImportBookResult
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
    bookGroups: JSONObject,
): ImportBookResult {
    return try {
        val existingBook = withContext(Dispatchers.IO) {
            findExistingBook(books = books, context = context, uri = uri)
        }
        if (existingBook != null) {
            val folderName = bookGroups.optString(existingBook.id, "").ifEmpty { RootLibraryName }
            return ImportBookResult.Duplicate(folderName)
        }

        val request = when (val inspection = withContext(Dispatchers.IO) { parser.inspectImportSource(uri) }) {
            is com.epubreader.data.parser.ImportInspectionResult.Ready -> inspection.request
            is com.epubreader.data.parser.ImportInspectionResult.Rejected -> {
                return ImportBookResult.Failed(inspection.reason.userMessage)
            }
        }

        val newBook = withContext(Dispatchers.IO) {
            parser.importBook(request)
        } ?: return ImportBookResult.Failed("Couldn't import this book.")

        settingsManager.updateBookGroup(
            newBook.id,
            if (selectedFolderName == RootLibraryName) null else selectedFolderName,
        )
        if (newBook.sourceFormat == BookFormat.PDF) {
            withContext(Dispatchers.IO) {
                parser.retryPdfConversion(newBook)
            }
        }
        ImportBookResult.Imported
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
    bookIds.forEach { id ->
        settingsManager.updateBookGroup(id, if (folderName == RootLibraryName) null else folderName)
    }
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
    idsToDelete.forEach { settingsManager.deleteBookData(it) }
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
