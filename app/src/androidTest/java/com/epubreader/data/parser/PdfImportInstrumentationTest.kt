package com.epubreader.data.parser

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.ConversionStatus
import com.epubreader.testing.PdfAndroidTestFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfImportInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val parser = EpubParser(context)
    private val booksDir get() = File(context.cacheDir, EPUB_BOOKS_DIR_NAME)
    private val createdPdfs = mutableListOf<File>()

    @Before
    fun setUp() {
        booksDir.deleteRecursively()
        booksDir.mkdirs()
    }

    @After
    fun tearDown() {
        booksDir.deleteRecursively()
        createdPdfs.forEach(File::delete)
    }

    @Test
    fun importBook_realPdfConversion_generatesEpubAndKeepsSourcePdfFallback() {
        val pdfFile = PdfAndroidTestFixtures.createTwoPageTextPdf(
            context = context,
            namePrefix = "parser-instrumentation",
        )
        createdPdfs += pdfFile

        val inspection = parser.inspectImportSource(Uri.fromFile(pdfFile))
        assertTrue(inspection is ImportInspectionResult.Ready)
        val request = (inspection as ImportInspectionResult.Ready).request
        assertEquals(BookFormat.PDF, request.format)

        val imported = requireNotNull(parser.importBook(request))
        assertEquals(BookFormat.PDF, imported.sourceFormat)
        assertEquals(BookFormat.PDF, imported.format)
        assertEquals(ConversionStatus.QUEUED, imported.conversionStatus)
        assertTrue(File(imported.rootPath, PDF_DOCUMENT_FILE_NAME).exists())
        assertTrue(File(imported.rootPath, GENERATED_EPUB_FILE_NAME).exists().not())

        val converted = requireNotNull(
            kotlinx.coroutines.runBlocking {
                parser.convertStoredPdfForBook(imported.id)
            },
        )
        val prepared = parser.prepareBookForReading(converted)

        assertEquals(BookFormat.PDF, prepared.sourceFormat)
        assertEquals(BookFormat.EPUB, prepared.format)
        assertEquals(ConversionStatus.READY, prepared.conversionStatus)
        assertTrue(prepared.hasPdfFallback)
        assertTrue(prepared.spineHrefs.isNotEmpty())
        assertNotNull(prepared.toc)
        assertTrue(File(prepared.rootPath, PDF_DOCUMENT_FILE_NAME).exists())
        assertTrue(File(prepared.rootPath, GENERATED_EPUB_FILE_NAME).exists())
    }
}
