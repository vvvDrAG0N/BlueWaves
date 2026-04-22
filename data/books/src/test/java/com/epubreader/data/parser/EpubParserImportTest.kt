package com.epubreader.data.parser

import android.net.Uri
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.ConversionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EpubParserImportTest : EpubParserFacadeTestSupport() {

    @Test
    fun parseAndExtract_importsIntoCacheFolder() {
        val epubFile = createMinimalEpub("import.epub")

        val book = parser.parseAndExtract(Uri.fromFile(epubFile))

        assertNotNull(book)
        val imported = requireNotNull(book)
        assertTrue(File(imported.rootPath, EPUB_ARCHIVE_FILE_NAME).exists())
        assertTrue(File(imported.rootPath, "metadata.json").exists())
        assertEquals("Test Book", imported.title)
        assertTrue(imported.spineHrefs.isNotEmpty())
        assertTrue(imported.toc.isNotEmpty())
    }

    @Test
    fun parseAndExtract_sameSource_reusesSameFolder() {
        val epubFile = createMinimalEpub("duplicate.epub")
        val uri = Uri.fromFile(epubFile)

        val first = requireNotNull(parser.parseAndExtract(uri))
        val second = requireNotNull(parser.parseAndExtract(uri))

        assertEquals(first.id, second.id)
        assertEquals(first.rootPath, second.rootPath)
        assertEquals(1, booksDir.listFiles()?.count { it.isDirectory })
    }

    @Test
    fun inspectImportSource_acceptsPdfFiles() {
        val pdfFile = copyFixturePdf("import.pdf")

        val inspection = parser.inspectImportSource(Uri.fromFile(pdfFile))

        assertTrue(inspection is ImportInspectionResult.Ready)
        val ready = inspection as ImportInspectionResult.Ready
        assertEquals(BookFormat.PDF, ready.request.format)
        assertTrue(ready.request.displayName?.endsWith("import.pdf") == true)
    }

    @Test
    fun importBook_pdfStaysInRawModeUntilBackgroundConversionRuns() {
        val pdfFile = copyFixturePdf("queued-import.pdf")
        val inspection = parser.inspectImportSource(Uri.fromFile(pdfFile)) as ImportInspectionResult.Ready

        val imported = requireNotNull(parser.importBook(inspection.request))

        assertEquals(BookFormat.PDF, imported.format)
        assertEquals(BookFormat.PDF, imported.sourceFormat)
        assertEquals(ConversionStatus.QUEUED, imported.conversionStatus)
        assertTrue(File(imported.rootPath, PDF_DOCUMENT_FILE_NAME).exists())
        assertFalse(File(imported.rootPath, GENERATED_EPUB_FILE_NAME).exists())
    }

    @Test
    fun importBook_failedCopy_cleansUpCreatedFolder() {
        val missingFile = File(context.cacheDir, "missing.epub")
        val book = parser.importBook(
            ImportRequest(
                bookId = "broken-import",
                uri = Uri.fromFile(missingFile),
                format = BookFormat.EPUB,
                displayName = "missing.epub",
            ),
        )

        assertNull(book)
        assertTrue(booksDir.listFiles()?.none { it.isDirectory } ?: true)
    }
}
