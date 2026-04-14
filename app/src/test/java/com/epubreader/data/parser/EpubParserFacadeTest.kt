package com.epubreader.data.parser

import android.net.Uri
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.ConversionStatus
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.TocItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class EpubParserFacadeTest {

    private val context = RuntimeEnvironment.getApplication()
    private val parser = EpubParser(context)
    private val booksDir get() = File(context.cacheDir, EPUB_BOOKS_DIR_NAME)

    @Before
    fun setUp() {
        booksDir.deleteRecursively()
        booksDir.mkdirs()
    }

    @After
    fun tearDown() {
        booksDir.deleteRecursively()
    }

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
    fun convertStoredPdfForBook_publishesGeneratedEpubWhenValidationPasses() {
        val conversionParser = EpubParser(
            context = context,
            pdfToEpubConverter = FakePdfToEpubConverter { _, _, outputFile, title, author, _ ->
                createGeneratedPdfFallbackEpub(outputFile, title, author)
                PdfConversionResult(
                    succeeded = true,
                    completedPages = 2,
                    totalPages = 2,
                    directTextPages = 2,
                    ocrPages = 0,
                )
            },
        )
        val pdfFile = copyFixturePdf("convert-success.pdf")
        val inspection = conversionParser.inspectImportSource(Uri.fromFile(pdfFile)) as ImportInspectionResult.Ready
        val imported = requireNotNull(conversionParser.importBook(inspection.request))

        val converted = requireNotNull(
            kotlinx.coroutines.runBlocking { conversionParser.convertStoredPdfForBook(imported.id) },
        )

        assertEquals(BookFormat.EPUB, converted.format)
        assertEquals(BookFormat.PDF, converted.sourceFormat)
        assertEquals(ConversionStatus.READY, converted.conversionStatus)
        assertTrue(File(converted.rootPath, GENERATED_EPUB_FILE_NAME).exists())
        assertTrue(File(converted.rootPath, PDF_DOCUMENT_FILE_NAME).exists())
    }

    @Test
    fun convertStoredPdfForBook_failedValidationKeepsRawPdfMode() {
        val conversionParser = EpubParser(
            context = context,
            pdfToEpubConverter = FakePdfToEpubConverter { _, _, outputFile, _, _, _ ->
                outputFile.writeText("not an epub")
                PdfConversionResult(
                    succeeded = true,
                    completedPages = 1,
                    totalPages = 1,
                    directTextPages = 1,
                    ocrPages = 0,
                )
            },
        )
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
            toc = listOf(TocItem("1. Page 1", "pages/page-0001.xhtml")),
            spineHrefs = listOf("pages/page-0001.xhtml"),
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
            toc = listOf(TocItem("1. Page 1", "pages/page-0001.xhtml")),
            spineHrefs = listOf("pages/page-0001.xhtml"),
            pageCount = 1,
        )
        saveBookMetadata(folder, book)
        File(folder, GENERATED_EPUB_FILE_NAME).writeBytes(byteArrayOf(1))
        File(folder, PDF_DOCUMENT_FILE_NAME).writeBytes("%PDF-1.4".toByteArray(StandardCharsets.UTF_8))

        val pdfMode = requireNotNull(parser.setBookRepresentation(book, BookRepresentation.PDF))
        val epubMode = requireNotNull(parser.setBookRepresentation(pdfMode, BookRepresentation.EPUB))

        assertEquals(BookFormat.PDF, pdfMode.format)
        assertEquals(BookFormat.EPUB, epubMode.format)
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

    private fun createMinimalEpub(name: String): File {
        val file = File(context.cacheDir, name)
        createMinimalEpubFile(file)
        return file
    }

    private fun createMinimalEpubFile(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            addStoredEntry(zip, "mimetype", "application/epub+zip")
            addEntry(
                zip,
                "META-INF/container.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent(),
            )
            addEntry(
                zip,
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    <dc:creator>Test Author</dc:creator>
                    <dc:identifier id="BookId">urn:uuid:test-book</dc:identifier>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine toc="ncx">
                    <itemref idref="chapter1"/>
                  </spine>
                </package>
                """.trimIndent(),
            )
            addEntry(
                zip,
                "OEBPS/toc.ncx",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <head/>
                  <docTitle><text>Test Book</text></docTitle>
                  <navMap>
                    <navPoint id="navPoint-1" playOrder="1">
                      <navLabel><text>Chapter 1</text></navLabel>
                      <content src="chapter1.xhtml"/>
                    </navPoint>
                  </navMap>
                </ncx>
                """.trimIndent(),
            )
            addEntry(
                zip,
                "OEBPS/chapter1.xhtml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>Chapter 1</title></head>
                  <body><p>Hello book.</p></body>
                </html>
                """.trimIndent(),
            )
        }
    }

    private fun copyFixturePdf(name: String): File {
        val file = File(context.cacheDir, name)
        val source = sequenceOf(
            File("test-books", "Horror Game Developer My games aren t that scary!.pdf"),
            File("../test-books", "Horror Game Developer My games aren t that scary!.pdf"),
        ).firstOrNull(File::exists)
        requireNotNull(source) { "Missing PDF fixture in test-books/" }
        source.copyTo(file, overwrite = true)
        return file
    }

    private fun copyFixturePdfTo(target: File) {
        val source = sequenceOf(
            File("test-books", "Horror Game Developer My games aren t that scary!.pdf"),
            File("../test-books", "Horror Game Developer My games aren t that scary!.pdf"),
        ).firstOrNull(File::exists)
        requireNotNull(source) { "Missing PDF fixture in test-books/" }
        source.copyTo(target, overwrite = true)
    }

    private fun addStoredEntry(zip: ZipOutputStream, name: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun addEntry(zip: ZipOutputStream, name: String, content: String) {
        val entry = ZipEntry(name)
        zip.putNextEntry(entry)
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun createGeneratedPdfFallbackEpub(
        outputFile: File,
        title: String,
        author: String,
    ) {
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            addStoredEntry(zip, "mimetype", "application/epub+zip")
            addEntry(
                zip,
                "META-INF/container.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent(),
            )
            addEntry(
                zip,
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title>
                    <dc:creator>$author</dc:creator>
                    <dc:identifier id="BookId">urn:uuid:generated-book</dc:identifier>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="page1" href="pages/page-0001.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine toc="ncx">
                    <itemref idref="page1"/>
                  </spine>
                </package>
                """.trimIndent(),
            )
            addEntry(
                zip,
                "OEBPS/toc.ncx",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <head/>
                  <docTitle><text>$title</text></docTitle>
                  <navMap>
                    <navPoint id="navPoint-1" playOrder="1">
                      <navLabel><text>Page 1</text></navLabel>
                      <content src="pages/page-0001.xhtml"/>
                    </navPoint>
                  </navMap>
                </ncx>
                """.trimIndent(),
            )
            addEntry(
                zip,
                "OEBPS/pages/page-0001.xhtml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>Page 1</title></head>
                  <body><p>Converted PDF text.</p></body>
                </html>
                """.trimIndent(),
            )
        }
    }

    private class FakePdfToEpubConverter(
        private val block: suspend (File, File, File, String, String, PdfConversionProgressListener) -> PdfConversionResult,
    ) : PdfToEpubConverter {
        override suspend fun convert(
            pdfFile: File,
            workspaceDir: File,
            outputFile: File,
            title: String,
            author: String,
            onProgress: PdfConversionProgressListener,
        ): PdfConversionResult {
            return block(pdfFile, workspaceDir, outputFile, title, author, onProgress)
        }
    }

}
