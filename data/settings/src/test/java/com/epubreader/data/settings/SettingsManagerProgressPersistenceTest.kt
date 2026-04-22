package com.epubreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.EpubBook
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsManagerProgressPersistenceTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val settingsManager = SettingsManager(context)

    @Before
    fun setUp() = runBlocking {
        resetDataStore()
    }

    @After
    fun tearDown() = runBlocking {
        resetDataStore()
    }

    @Test
    fun saveBookProgress_persistsEpubAndPdfRepresentationsSeparately() = runBlocking {
        settingsManager.saveBookProgress(
            bookId = "shared-book",
            progress = BookProgress(
                scrollIndex = 3,
                scrollOffset = 11,
                lastChapterHref = "Text/ch3.xhtml",
            ),
            representation = BookRepresentation.EPUB,
        )
        settingsManager.saveBookProgress(
            bookId = "shared-book",
            progress = BookProgress(
                scrollIndex = 17,
                scrollOffset = 29,
                lastChapterHref = null,
            ),
            representation = BookRepresentation.PDF,
        )

        val epubProgress = settingsManager.getBookProgress("shared-book", BookRepresentation.EPUB).first()
        val pdfProgress = settingsManager.getBookProgress("shared-book", BookRepresentation.PDF).first()

        assertEquals(3, epubProgress.scrollIndex)
        assertEquals(11, epubProgress.scrollOffset)
        assertEquals("Text/ch3.xhtml", epubProgress.lastChapterHref)

        assertEquals(17, pdfProgress.scrollIndex)
        assertEquals(29, pdfProgress.scrollOffset)
        assertNull(pdfProgress.lastChapterHref)
    }

    @Test
    fun saveBookProgress_keepsLegacyEpubKeysReadable() = runBlocking {
        settingsManager.saveBookProgress(
            bookId = "legacy-book",
            progress = BookProgress(
                scrollIndex = 5,
                scrollOffset = 9,
                lastChapterHref = "Text/ch5.xhtml",
            ),
            representation = BookRepresentation.EPUB,
        )

        val preferences = context.settingsDataStore.data.first()
        val legacyKeys = legacyBookProgressPreferenceKeys("legacy-book")

        assertEquals(5, preferences[legacyKeys.scrollIndex])
        assertEquals(9, preferences[legacyKeys.scrollOffset])
        assertEquals("Text/ch5.xhtml", preferences[legacyKeys.chapter])
    }

    @Test
    fun deleteBookData_clearsAllRepresentations() = runBlocking {
        settingsManager.saveBookProgress(
            bookId = "book-to-delete",
            progress = BookProgress(scrollIndex = 2, scrollOffset = 4, lastChapterHref = "Text/ch2.xhtml"),
            representation = BookRepresentation.EPUB,
        )
        settingsManager.saveBookProgress(
            bookId = "book-to-delete",
            progress = BookProgress(scrollIndex = 8, scrollOffset = 16, lastChapterHref = null),
            representation = BookRepresentation.PDF,
        )

        settingsManager.deleteBookData("book-to-delete")

        val epubProgress = settingsManager.getBookProgress("book-to-delete", BookRepresentation.EPUB).first()
        val pdfProgress = settingsManager.getBookProgress("book-to-delete", BookRepresentation.PDF).first()

        assertEquals(BookProgress(), epubProgress)
        assertEquals(BookProgress(), pdfProgress)
    }

    @Test
    fun globalSettings_ignoresProgressWrites_butEmitsForActualSettingChanges() = runBlocking {
        val emissions = mutableListOf<Int>()
        val job = launch {
            settingsManager.globalSettings.collect { settings ->
                emissions += settings.fontSize
            }
        }

        withTimeout(1_000) {
            while (emissions.isEmpty()) {
                delay(10)
            }
        }

        settingsManager.saveBookProgress(
            bookId = "progress-only",
            progress = BookProgress(scrollIndex = 4, scrollOffset = 8, lastChapterHref = "Text/ch4.xhtml"),
            representation = BookRepresentation.EPUB,
        )
        delay(200)
        assertEquals(listOf(18), emissions)

        settingsManager.updateGlobalSettings { it.copy(fontSize = 22) }

        withTimeout(1_000) {
            while (emissions.size < 2) {
                delay(10)
            }
        }
        assertEquals(listOf(18, 22), emissions)
        job.cancel()
    }

    @Test
    fun updateBookGroups_andDeleteBooksData_batchMutationsPersistCorrectly() = runBlocking {
        settingsManager.updateBookGroups(setOf("book-1", "book-2"), "Sci-Fi")
        settingsManager.saveBookProgress(
            bookId = "book-1",
            progress = BookProgress(scrollIndex = 1, scrollOffset = 2, lastChapterHref = "Text/ch1.xhtml"),
            representation = BookRepresentation.EPUB,
        )
        settingsManager.saveBookProgress(
            bookId = "book-2",
            progress = BookProgress(scrollIndex = 3, scrollOffset = 4, lastChapterHref = "Text/ch3.xhtml"),
            representation = BookRepresentation.EPUB,
        )

        val groupedSettings = settingsManager.globalSettings.first()
        assertTrue(groupedSettings.bookGroups.contains("\"book-1\":\"Sci-Fi\""))
        assertTrue(groupedSettings.bookGroups.contains("\"book-2\":\"Sci-Fi\""))

        settingsManager.deleteBooksData(setOf("book-1", "book-2"))

        assertEquals(
            BookProgress(),
            settingsManager.getBookProgress("book-1", BookRepresentation.EPUB).first(),
        )
        assertEquals(
            BookProgress(),
            settingsManager.getBookProgress("book-2", BookRepresentation.EPUB).first(),
        )
        assertEquals("{}", settingsManager.globalSettings.first().bookGroups)
    }

    @Test
    fun observeBookProgresses_returnsSingleMapForVisibleBooks() = runBlocking {
        val books = listOf(
            EpubBook(
                id = "visible-1",
                title = "Visible 1",
                author = "Author",
                coverPath = null,
                rootPath = "/books/visible-1",
                format = BookFormat.EPUB,
            ),
            EpubBook(
                id = "visible-2",
                title = "Visible 2",
                author = "Author",
                coverPath = null,
                rootPath = "/books/visible-2",
                format = BookFormat.EPUB,
            ),
        )
        settingsManager.saveBookProgress(
            bookId = "visible-1",
            progress = BookProgress(scrollIndex = 7, scrollOffset = 0, lastChapterHref = "Text/ch7.xhtml"),
            representation = BookRepresentation.EPUB,
        )

        val progressByBookId = settingsManager.observeBookProgresses(books).first()

        assertEquals(2, progressByBookId.size)
        assertEquals(7, progressByBookId.getValue("visible-1").scrollIndex)
        assertEquals(BookProgress(), progressByBookId.getValue("visible-2"))
    }

    private suspend fun resetDataStore() {
        context.settingsDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
