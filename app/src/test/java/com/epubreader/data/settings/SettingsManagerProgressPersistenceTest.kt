package com.epubreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.BookRepresentation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private suspend fun resetDataStore() {
        context.settingsDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
