package com.epubreader.data.parser

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpubParserLookupTest : EpubParserFacadeTestSupport() {

    @Test
    fun loadBookById_returnsImportedBookMetadata() {
        val epubFile = createMinimalEpub("lookup.epub")
        val importedBook = requireNotNull(parser.parseAndExtract(Uri.fromFile(epubFile)))

        val loadedBook = parser.loadBookById(importedBook.id)

        assertNotNull(loadedBook)
        assertEquals(importedBook.id, loadedBook?.id)
        assertEquals(importedBook.title, loadedBook?.title)
        assertEquals(importedBook.rootPath, loadedBook?.rootPath)
    }

    @Test
    fun loadBookById_returnsNullWhenBookIsMissing() {
        assertNull(parser.loadBookById("missing-book"))
    }
}
