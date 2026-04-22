package com.epubreader.data.parser

import android.net.Uri
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.TocItem
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.After
import org.junit.Before
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

abstract class EpubParserFacadeTestSupport {

    protected val context = RuntimeEnvironment.getApplication()
    protected lateinit var parser: EpubParser
    protected val booksDir get() = File(context.cacheDir, EPUB_BOOKS_DIR_NAME)

    @Before
    fun setUpFacadeParser() {
        booksDir.deleteRecursively()
        booksDir.mkdirs()
        parser = EpubParser.create(context)
    }

    @After
    fun tearDownFacadeParser() {
        booksDir.deleteRecursively()
    }

    protected fun createGeneratedPdfParser(
        completedPages: Int,
        totalPages: Int,
    ): EpubParser {
        return EpubParser(
            context = context,
            pdfLegacyBridge = FakePdfLegacyBridge(
                completedPages = completedPages,
                totalPages = totalPages,
                directTextPages = completedPages,
                ocrPages = 0,
            ) { outputFile, title, author ->
                createGeneratedPdfFallbackEpub(outputFile, title, author)
            },
        )
    }

    protected fun createInvalidGeneratedPdfParser(): EpubParser {
        return EpubParser(
            context = context,
            pdfLegacyBridge = FakePdfLegacyBridge(
                completedPages = 1,
                totalPages = 1,
                directTextPages = 1,
                ocrPages = 0,
            ) { outputFile, _, _ ->
                outputFile.writeText("not an epub")
            },
        )
    }

    protected fun createMinimalEpub(name: String): File {
        val file = File(context.cacheDir, name)
        createMinimalEpubFile(file)
        return file
    }

    protected fun createImageChapterEpub(name: String): File {
        val file = File(context.cacheDir, name)
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
                    <dc:title>Image Chapter Book</dc:title>
                    <dc:creator>Test Author</dc:creator>
                    <dc:identifier id="BookId">urn:uuid:image-book</dc:identifier>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="pixel" href="images/pixel.png" media-type="image/png"/>
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
                  <docTitle><text>Image Chapter Book</text></docTitle>
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
                  <body>
                    <p>Hello image chapter.</p>
                    <img src="images/pixel.png" />
                    <p>Goodbye image chapter.</p>
                  </body>
                </html>
                """.trimIndent(),
            )
            zip.putNextEntry(ZipEntry("OEBPS/images/pixel.png"))
            zip.write(onePixelPngBytes())
            zip.closeEntry()
        }
        return file
    }

    protected fun copyFixturePdf(name: String): File {
        val file = File(context.cacheDir, name)
        writeFixturePdf(file)
        return file
    }

    protected fun copyFixturePdfTo(target: File) {
        writeFixturePdf(target)
    }

    protected fun createGeneratedPdfFallbackEpub(
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
                    <item id="section1" href="sections/section-0001.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine toc="ncx">
                    <itemref idref="section1"/>
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
                      <content src="sections/section-0001.xhtml#page-0001"/>
                    </navPoint>
                  </navMap>
                </ncx>
                """.trimIndent(),
            )
            addEntry(
                zip,
                "OEBPS/sections/section-0001.xhtml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>Page 1</title></head>
                  <body><section><div id="page-0001">Page 1</div><p>Converted PDF text.</p></section></body>
                </html>
                """.trimIndent(),
            )
        }
    }

    protected fun createUnreadableGeneratedPdfFallbackEpub(
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
                    <dc:identifier id="BookId">urn:uuid:broken-generated-book</dc:identifier>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="section1" href="sections/section-0001.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine toc="ncx">
                    <itemref idref="section1"/>
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
                      <content src="sections/section-0001.xhtml#page-0001"/>
                    </navPoint>
                  </navMap>
                </ncx>
                """.trimIndent(),
            )
            addEntry(
                zip,
                "OEBPS/sections/section-0001.xhtml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title></title></head>
                  <body><section id="page-0001"></section></body>
                </html>
                """.trimIndent(),
            )
        }
    }

    protected fun createMinimalEpubFile(file: File) {
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

    protected fun addStoredEntry(zip: ZipOutputStream, name: String, content: String) {
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

    protected fun addEntry(zip: ZipOutputStream, name: String, content: String) {
        val entry = ZipEntry(name)
        zip.putNextEntry(entry)
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeFixturePdf(target: File) {
        target.parentFile?.mkdirs()
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.save(target)
        }
    }

    private fun onePixelPngBytes(): ByteArray {
        return Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+nX8QAAAAASUVORK5CYII=",
        )
    }

    private class FakePdfLegacyBridge(
        private val completedPages: Int,
        private val totalPages: Int,
        private val directTextPages: Int,
        private val ocrPages: Int,
        private val outputWriter: (File, String, String) -> Unit,
    ) : PdfLegacyBridge {
        override val workspaceDirName: String = "pdf_conversion_workspace"

        override suspend fun convert(
            pdfFile: File,
            workspaceDir: File,
            outputFile: File,
            title: String,
            author: String,
            onProgress: PdfConversionProgressListener,
        ): PdfConversionResult {
            outputWriter(outputFile, title, author)
            return PdfConversionResult(
                succeeded = true,
                completedPages = completedPages,
                totalPages = totalPages,
                directTextPages = directTextPages,
                ocrPages = ocrPages,
            )
        }

        override fun enqueue(bookId: String) = Unit

        override fun cancel(bookId: String) = Unit

        override fun uniqueWorkName(bookId: String): String = "test-$bookId"
    }
}
