package com.epubreader.data.parser

import android.net.Uri
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.TocItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EpubParserScanCacheTest : EpubParserFacadeTestSupport() {

    @Test
    fun scanBooks_returnsCachedMetadata() {
        val folder = File(booksDir, "cached-book").apply { mkdirs() }
        saveBookMetadata(
            folder,
            EpubBook(
                id = "cached-book",
                title = "Cached Title",
                author = "Cached Author",
                coverPath = null,
                rootPath = folder.absolutePath,
                toc = listOf(TocItem("1. Cached", "chapter1.xhtml")),
                spineHrefs = listOf("chapter1.xhtml"),
                dateAdded = 10L,
                lastRead = 20L,
            ),
        )
        File(folder, EPUB_ARCHIVE_FILE_NAME).writeBytes(byteArrayOf(1))

        val books = parser.scanBooks()

        assertEquals(1, books.size)
        assertEquals("cached-book", books.first().id)
        assertEquals("Cached Title", books.first().title)
    }

    @Test
    fun scanBooks_returnsCachedPdfMetadata() {
        val folder = File(booksDir, "cached-pdf").apply { mkdirs() }
        saveBookMetadata(
            folder,
            EpubBook(
                id = "cached-pdf",
                title = "Cached PDF",
                author = "Cached Author",
                coverPath = null,
                rootPath = folder.absolutePath,
                format = BookFormat.PDF,
                pageCount = 42,
                dateAdded = 10L,
                lastRead = 20L,
            ),
        )
        copyFixturePdfTo(File(folder, PDF_DOCUMENT_FILE_NAME))

        val books = parser.scanBooks()

        assertEquals(1, books.size)
        assertEquals(BookFormat.PDF, books.first().format)
        assertEquals(42, books.first().pageCount)
    }

    @Test
    fun parseChapter_cachesResults_andMaterializesImagesToChapterMedia() {
        val epubFile = createImageChapterEpub("image-chapter.epub")
        val imported = requireNotNull(parser.parseAndExtract(Uri.fromFile(epubFile)))

        val first = parser.parseChapter(imported.rootPath, imported.spineHrefs.first())
        val second = parser.parseChapter(imported.rootPath, imported.spineHrefs.first())

        assertTrue(first === second)
        val imageElement = first.filterIsInstance<ChapterElement.Image>().single()
        val imageFile = File(imageElement.filePath)
        assertTrue(imageFile.exists())
        assertEquals("chapter_media", imageFile.parentFile?.name)
        assertTrue(first.filterIsInstance<ChapterElement.Text>().any { it.content.contains("Hello image chapter.") })
    }

    @Test
    fun parseChapter_producesDeterministicElementIdsAcrossIndependentParsers() {
        val epubFile = createImageChapterEpub("stable-ids.epub")
        val imported = requireNotNull(parser.parseAndExtract(Uri.fromFile(epubFile)))
        val independentParser = EpubParser.create(context)

        val firstIds = parser.parseChapter(imported.rootPath, imported.spineHrefs.first()).map(ChapterElement::id)
        val secondIds = independentParser.parseChapter(imported.rootPath, imported.spineHrefs.first()).map(ChapterElement::id)

        assertEquals(firstIds, secondIds)
    }
}
