package com.epubreader.data.parser

import android.net.Uri
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.ConversionStatus
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.TocItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
class EpubParserPdfFallbackTest : EpubParserFacadeTestSupport() {

    @Test
    fun convertStoredPdfForBook_publishesGeneratedEpubWhenValidationPasses() {
        val conversionParser = createGeneratedPdfParser(completedPages = 2, totalPages = 2)
        val pdfFile = copyFixturePdf("convert-success.pdf")
        val inspection = conversionParser.inspectImportSource(Uri.fromFile(pdfFile)) as ImportInspectionResult.Ready
        val imported = requireNotNull(conversionParser.importBook(inspection.request))

        val converted = requireNotNull(
            kotlinx.coroutines.runBlocking { conversionParser.convertStoredPdfForBook(imported.id) },
        )

        assertEquals(BookFormat.PDF, converted.format)
        assertEquals(BookFormat.PDF, converted.sourceFormat)
        assertEquals(ConversionStatus.READY, converted.conversionStatus)
        assertTrue(converted.canOpenGeneratedEpub)
        assertTrue(File(converted.rootPath, GENERATED_EPUB_FILE_NAME).exists())
        assertTrue(File(converted.rootPath, PDF_DOCUMENT_FILE_NAME).exists())
    }

    @Test
    fun parseChapter_readsGeneratedEpubForConvertedPdf() {
        val conversionParser = createGeneratedPdfParser(completedPages = 1, totalPages = 1)
        val pdfFile = copyFixturePdf("convert-parse.pdf")
        val inspection = conversionParser.inspectImportSource(Uri.fromFile(pdfFile)) as ImportInspectionResult.Ready
        val imported = requireNotNull(conversionParser.importBook(inspection.request))

        val converted = requireNotNull(
            kotlinx.coroutines.runBlocking { conversionParser.convertStoredPdfForBook(imported.id) },
        )
        val elements = conversionParser.parseChapter(converted.rootPath, converted.spineHrefs.first())

        assertTrue(
            elements
                .filterIsInstance<ChapterElement.Text>()
                .any { it.content.contains("Converted PDF text.") },
        )
    }

    @Test
    fun prepareBookForReading_demotesConvertedPdfWhenGeneratedEpubHasNoReadableSections() {
        val folder = File(booksDir, "broken-generated-open").apply { mkdirs() }
        val book = EpubBook(
            id = "broken-generated-open",
            title = "Broken Generated EPUB",
            author = "Author",
            coverPath = null,
            rootPath = folder.absolutePath,
            format = BookFormat.EPUB,
            sourceFormat = BookFormat.PDF,
            conversionStatus = ConversionStatus.READY,
            hasPdfFallback = true,
            toc = listOf(TocItem("1. Page 1", "sections/section-0001.xhtml")),
            spineHrefs = listOf("sections/section-0001.xhtml"),
            pageCount = 1,
        )
        saveBookMetadata(folder, book)
        createUnreadableGeneratedPdfFallbackEpub(
            outputFile = File(folder, GENERATED_EPUB_FILE_NAME),
            title = book.title,
            author = book.author,
        )
        copyFixturePdfTo(File(folder, PDF_DOCUMENT_FILE_NAME))

        val prepared = parser.prepareBookForReading(book)

        assertEquals(BookFormat.PDF, prepared.format)
        assertEquals(ConversionStatus.FAILED, prepared.conversionStatus)
        assertTrue(prepared.hasPdfFallback)
    }

    @Test
    fun scanBooks_demotesConvertedPdfWhenGeneratedEpubHasNoReadableSections() {
        val folder = File(booksDir, "broken-generated-scan").apply { mkdirs() }
        saveBookMetadata(
            folder,
            EpubBook(
                id = "broken-generated-scan",
                title = "Broken Generated EPUB",
                author = "Author",
                coverPath = null,
                rootPath = folder.absolutePath,
                format = BookFormat.EPUB,
                sourceFormat = BookFormat.PDF,
                conversionStatus = ConversionStatus.READY,
                hasPdfFallback = true,
                toc = listOf(TocItem("1. Page 1", "sections/section-0001.xhtml")),
                spineHrefs = listOf("sections/section-0001.xhtml"),
                pageCount = 1,
            ),
        )
        createUnreadableGeneratedPdfFallbackEpub(
            outputFile = File(folder, GENERATED_EPUB_FILE_NAME),
            title = "Broken Generated EPUB",
            author = "Author",
        )
        copyFixturePdfTo(File(folder, PDF_DOCUMENT_FILE_NAME))

        val scanned = parser.scanBooks().single()

        assertEquals(BookFormat.PDF, scanned.format)
        assertEquals(ConversionStatus.FAILED, scanned.conversionStatus)
        assertTrue(scanned.hasPdfFallback)
    }

    @Test
    fun convertStoredPdfForBook_failedValidationKeepsRawPdfMode() {
        val conversionParser = createInvalidGeneratedPdfParser()
        val pdfFile = copyFixturePdf("convert-failure.pdf")
        val inspection = conversionParser.inspectImportSource(Uri.fromFile(pdfFile)) as ImportInspectionResult.Ready
        val imported = requireNotNull(conversionParser.importBook(inspection.request))

        val converted = requireNotNull(
            kotlinx.coroutines.runBlocking { conversionParser.convertStoredPdfForBook(imported.id) },
        )

        assertEquals(BookFormat.PDF, converted.format)
        assertEquals(ConversionStatus.FAILED, converted.conversionStatus)
        assertFalse(File(converted.rootPath, GENERATED_EPUB_FILE_NAME).exists())
    }

    @Test
    fun resolveStoredBookFile_prefersGeneratedEpubForConvertedPdf() {
        val folder = File(booksDir, "converted-reader").apply { mkdirs() }
        val book = EpubBook(
            id = "converted-reader",
            title = "Converted PDF",
            author = "Author",
            coverPath = null,
            rootPath = folder.absolutePath,
            format = BookFormat.EPUB,
            sourceFormat = BookFormat.PDF,
            conversionStatus = ConversionStatus.READY,
            hasPdfFallback = true,
            toc = listOf(TocItem("1. Page 1", "sections/section-0001.xhtml")),
            spineHrefs = listOf("sections/section-0001.xhtml"),
            pageCount = 1,
        )
        File(folder, GENERATED_EPUB_FILE_NAME).writeBytes(byteArrayOf(1))
        File(folder, PDF_DOCUMENT_FILE_NAME).writeBytes("%PDF-1.4".toByteArray(StandardCharsets.UTF_8))

        val resolved = parser.resolveStoredBookFile(book)

        assertEquals(GENERATED_EPUB_FILE_NAME, resolved.name)
    }

    @Test
    fun setBookRepresentation_switchesBetweenGeneratedEpubAndSourcePdf() {
        val folder = File(booksDir, "switchable-book").apply { mkdirs() }
        val book = EpubBook(
            id = "switchable-book",
            title = "Switchable PDF",
            author = "Author",
            coverPath = null,
            rootPath = folder.absolutePath,
            format = BookFormat.EPUB,
            sourceFormat = BookFormat.PDF,
            conversionStatus = ConversionStatus.READY,
            hasPdfFallback = true,
            toc = listOf(TocItem("1. Page 1", "sections/section-0001.xhtml")),
            spineHrefs = listOf("sections/section-0001.xhtml"),
            pageCount = 1,
        )
        saveBookMetadata(folder, book)
        createGeneratedPdfFallbackEpub(
            outputFile = File(folder, GENERATED_EPUB_FILE_NAME),
            title = book.title,
            author = book.author,
        )
        File(folder, PDF_DOCUMENT_FILE_NAME).writeBytes("%PDF-1.4".toByteArray(StandardCharsets.UTF_8))

        val pdfMode = requireNotNull(parser.setBookRepresentation(book, BookRepresentation.PDF))
        val epubMode = requireNotNull(parser.setBookRepresentation(pdfMode, BookRepresentation.EPUB))

        assertEquals(BookFormat.PDF, pdfMode.format)
        assertEquals(BookFormat.EPUB, epubMode.format)
    }
}
