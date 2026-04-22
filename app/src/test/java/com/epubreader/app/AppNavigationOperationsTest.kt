package com.epubreader.app

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.ConversionStatus
import com.epubreader.core.model.EpubBook
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.parser.ImportFailureReason
import com.epubreader.data.parser.ImportInspectionResult
import com.epubreader.data.parser.ImportRequest
import com.epubreader.data.settings.SettingsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class AppNavigationOperationsTest {

    @Test
    fun importBook_duplicate_returnsFolderName() = runBlocking {
        val uri = Uri.parse("content://books/existing.epub")
        val fileSize = 123L
        val existingId = buildBookIdForTest(uri, fileSize)
        val (context, _, _, parser, settingsManager) = mockImportDeps(uri, fileSize)

        val result = importBookIntoLibrary(
            books = listOf(book(id = existingId)),
            context = context,
            uri = uri,
            parser = parser,
            settingsManager = settingsManager,
            selectedFolderName = RootLibraryName,
            bookGroups = mapOf(existingId to "Sci-Fi"),
        )

        assertEquals(ImportBookResult.Duplicate("Sci-Fi"), result)
        verify(exactly = 0) { parser.parseAndExtract(any()) }
        coVerify(exactly = 0) { settingsManager.updateBookGroup(any(), any()) }
    }

    @Test
    fun importBook_root_putsBookInMyLibrary() = runBlocking {
        val uri = Uri.parse("content://books/new-root.epub")
        val fileSize = 456L
        val newBook = book(id = "new-root")
        val (context, _, _, parser, settingsManager) = mockImportDeps(uri, fileSize)
        every { parser.inspectImportSource(uri) } returns ImportInspectionResult.Ready(
            ImportRequest(
                bookId = "new-root",
                uri = uri,
                format = BookFormat.EPUB,
                displayName = "new-root.epub",
            ),
        )
        every { parser.importBook(any()) } returns newBook
        coEvery { settingsManager.updateBookGroup(any(), any()) } just runs

        val result = importBookIntoLibrary(
            books = emptyList(),
            context = context,
            uri = uri,
            parser = parser,
            settingsManager = settingsManager,
            selectedFolderName = RootLibraryName,
            bookGroups = emptyMap(),
        )

        assertEquals(ImportBookResult.Imported(newBook), result)
        coVerify { settingsManager.updateBookGroup("new-root", null) }
    }

    @Test
    fun importBook_selectedFolder_putsBookInFolder() = runBlocking {
        val uri = Uri.parse("content://books/new-folder.epub")
        val fileSize = 789L
        val newBook = book(id = "new-folder")
        val (context, _, _, parser, settingsManager) = mockImportDeps(uri, fileSize)
        every { parser.inspectImportSource(uri) } returns ImportInspectionResult.Ready(
            ImportRequest(
                bookId = "new-folder",
                uri = uri,
                format = BookFormat.EPUB,
                displayName = "new-folder.epub",
            ),
        )
        every { parser.importBook(any()) } returns newBook
        coEvery { settingsManager.updateBookGroup(any(), any()) } just runs

        val result = importBookIntoLibrary(
            books = emptyList(),
            context = context,
            uri = uri,
            parser = parser,
            settingsManager = settingsManager,
            selectedFolderName = "Sci-Fi",
            bookGroups = emptyMap(),
        )

        assertEquals(ImportBookResult.Imported(newBook), result)
        coVerify { settingsManager.updateBookGroup("new-folder", "Sci-Fi") }
    }

    @Test
    fun importBook_pdfIsRejectedWhileSupportIsDisabled() = runBlocking {
        val uri = Uri.parse("content://books/new-paper.pdf")
        val fileSize = 654L
        val (context, _, _, parser, settingsManager) = mockImportDeps(uri, fileSize)
        every { parser.inspectImportSource(uri) } returns ImportInspectionResult.Ready(
            ImportRequest(
                bookId = "new-paper",
                uri = uri,
                format = BookFormat.PDF,
                displayName = "new-paper.pdf",
            ),
        )

        val result = importBookIntoLibrary(
            books = emptyList(),
            context = context,
            uri = uri,
            parser = parser,
            settingsManager = settingsManager,
            selectedFolderName = RootLibraryName,
            bookGroups = emptyMap(),
        )

        assertEquals(
            ImportBookResult.Failed("PDF support is temporarily disabled while we prepare a safer refactor."),
            result,
        )
        verify(exactly = 0) { parser.importBook(any()) }
        verify(exactly = 0) { parser.retryPdfConversion(any()) }
        coVerify(exactly = 0) { settingsManager.updateBookGroup(any(), any()) }
    }

    @Test
    fun importBook_unsupportedFile_surfacesReason() = runBlocking {
        val uri = Uri.parse("content://books/document.pdf")
        val fileSize = 111L
        val (context, _, _, parser, settingsManager) = mockImportDeps(uri, fileSize)
        every { parser.inspectImportSource(uri) } returns ImportInspectionResult.Rejected(
            ImportFailureReason.UnsupportedFileType,
        )

        val result = importBookIntoLibrary(
            books = emptyList(),
            context = context,
            uri = uri,
            parser = parser,
            settingsManager = settingsManager,
            selectedFolderName = RootLibraryName,
            bookGroups = emptyMap(),
        )

        assertEquals(
            ImportBookResult.Failed("This file type is not supported. Import an EPUB or a ZIP archive containing one supported EPUB."),
            result,
        )
        verify(exactly = 0) { parser.importBook(any()) }
        coVerify(exactly = 0) { settingsManager.updateBookGroup(any(), any()) }
    }

    @Test
    fun touchLastRead_keepsIdentity_updatesTime() = runBlocking {
        val parser = mockk<EpubParser>(relaxed = true)
        val before = book(id = "book-1", lastRead = 100L)

        val updated = touchBookLastRead(parser, before)

        assertEquals(before.id, updated.id)
        assertEquals(before.title, updated.title)
        assertTrue(updated.lastRead >= before.lastRead)
        verify { parser.updateLastRead(updated) }
    }

    @Test
    fun deleteSelectedBooks_cancelsPdfWorkBeforeDeleting() = runBlocking {
        val parser = mockk<EpubParser>(relaxed = true)
        val settingsManager = mockk<SettingsManager>(relaxed = true)
        val books = listOf(book(id = "book-1"), book(id = "book-2"))

        deleteSelectedBooks(
            parser = parser,
            settingsManager = settingsManager,
            books = books,
            selectedBookIds = setOf("book-1", "book-2"),
        )

        verify { parser.cancelPdfConversion("book-1") }
        verify { parser.cancelPdfConversion("book-2") }
        verify { parser.deleteBook(match { it.id == "book-1" }) }
        verify { parser.deleteBook(match { it.id == "book-2" }) }
        coVerify { settingsManager.deleteBooksData(setOf("book-1", "book-2")) }
    }

    @Test
    fun moveBooksToFolder_usesBulkSettingsUpdate() = runBlocking {
        val settingsManager = mockk<SettingsManager>(relaxed = true)

        moveBooksToFolder(
            settingsManager = settingsManager,
            bookIds = setOf("book-1", "book-2"),
            folderName = "Sci-Fi",
        )

        coVerify { settingsManager.updateBookGroups(setOf("book-1", "book-2"), "Sci-Fi") }
    }

    private fun mockImportDeps(
        uri: Uri,
        fileSize: Long,
    ): Quintet<Context, ContentResolver, ParcelFileDescriptor, EpubParser, SettingsManager> {
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val descriptor = mockk<ParcelFileDescriptor>()
        val parser = mockk<EpubParser>(relaxed = true)
        val settingsManager = mockk<SettingsManager>(relaxed = true)

        every { context.contentResolver } returns resolver
        every { resolver.openFileDescriptor(uri, "r") } returns descriptor
        every { descriptor.statSize } returns fileSize
        every { descriptor.close() } just runs

        return Quintet(context, resolver, descriptor, parser, settingsManager)
    }

    private fun buildBookIdForTest(uri: Uri, fileSize: Long): String {
        val rawId = "$uri$fileSize"
        return MessageDigest.getInstance("MD5")
            .digest(rawId.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun book(
        id: String,
        lastRead: Long = 0L,
        format: BookFormat = BookFormat.EPUB,
        sourceFormat: BookFormat = format,
        conversionStatus: ConversionStatus = ConversionStatus.NONE,
    ): EpubBook {
        return EpubBook(
            id = id,
            title = "Title $id",
            author = "Author",
            coverPath = null,
            rootPath = "/books/$id",
            format = format,
            sourceFormat = sourceFormat,
            conversionStatus = conversionStatus,
            dateAdded = 1L,
            lastRead = lastRead,
        )
    }

    private data class Quintet<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
    )
}
