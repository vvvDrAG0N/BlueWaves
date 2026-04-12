package com.epubreader.app

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.epubreader.core.model.EpubBook
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
            bookGroups = JSONObject("""{"$existingId":"Sci-Fi"}"""),
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
        every { parser.parseAndExtract(uri) } returns newBook
        coEvery { settingsManager.updateBookGroup(any(), any()) } just runs

        val result = importBookIntoLibrary(
            books = emptyList(),
            context = context,
            uri = uri,
            parser = parser,
            settingsManager = settingsManager,
            selectedFolderName = RootLibraryName,
            bookGroups = JSONObject(),
        )

        assertEquals(ImportBookResult.Imported, result)
        coVerify { settingsManager.updateBookGroup("new-root", null) }
    }

    @Test
    fun importBook_selectedFolder_putsBookInFolder() = runBlocking {
        val uri = Uri.parse("content://books/new-folder.epub")
        val fileSize = 789L
        val newBook = book(id = "new-folder")
        val (context, _, _, parser, settingsManager) = mockImportDeps(uri, fileSize)
        every { parser.parseAndExtract(uri) } returns newBook
        coEvery { settingsManager.updateBookGroup(any(), any()) } just runs

        val result = importBookIntoLibrary(
            books = emptyList(),
            context = context,
            uri = uri,
            parser = parser,
            settingsManager = settingsManager,
            selectedFolderName = "Sci-Fi",
            bookGroups = JSONObject(),
        )

        assertEquals(ImportBookResult.Imported, result)
        coVerify { settingsManager.updateBookGroup("new-folder", "Sci-Fi") }
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

    private fun book(id: String, lastRead: Long = 0L): EpubBook {
        return EpubBook(
            id = id,
            title = "Title $id",
            author = "Author",
            coverPath = null,
            rootPath = "/books/$id",
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
