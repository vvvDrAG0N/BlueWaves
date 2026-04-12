package com.epubreader.data.parser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.epubreader.core.model.ChapterElement
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ParserIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
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
    fun reparseBookAndParseChapter_handleOebpsLayout() {
        val bookFolder = createBookFolder(
            bookId = "parser-oebps-${UUID.randomUUID()}",
            containerPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            chapterHref = "Text/ch1.xhtml",
            chapterEntryPath = "OEBPS/Text/ch1.xhtml",
            title = "OEBPS Book",
            body = """
                <h1>OEBPS Heading</h1>
                <p>OEBPS Paragraph</p>
            """.trimIndent(),
        )

        val book = requireNotNull(parser.reparseBook(bookFolder))
        val elements = parser.parseChapter(book.rootPath, requireNotNull(book.spineHrefs.firstOrNull()))
        val scanned = parser.scanBooks()

        assertEquals("OEBPS Book", book.title)
        assertTrue(book.spineHrefs.contains("Text/ch1.xhtml"))
        assertTrue(book.toc.isNotEmpty())
        assertTrue(elements.filterIsInstance<ChapterElement.Text>().any { it.content.contains("OEBPS Heading") })
        assertTrue(elements.filterIsInstance<ChapterElement.Text>().any { it.content.contains("OEBPS Paragraph") })
        assertTrue(scanned.any { it.id == book.id && it.title == "OEBPS Book" })
    }

    @Test
    fun reparseBookAndParseChapter_handleOpsLayout() {
        val bookFolder = createBookFolder(
            bookId = "parser-ops-${UUID.randomUUID()}",
            containerPath = "OPS/content.opf",
            tocPath = "OPS/toc.ncx",
            chapterHref = "chapter1.xhtml",
            chapterEntryPath = "OPS/chapter1.xhtml",
            title = "OPS Book",
            body = """
                <h1>OPS Heading</h1>
                <p>OPS Paragraph</p>
            """.trimIndent(),
        )

        val book = requireNotNull(parser.reparseBook(bookFolder))
        val elements = parser.parseChapter(book.rootPath, requireNotNull(book.spineHrefs.firstOrNull()))

        assertEquals("OPS Book", book.title)
        assertTrue(book.spineHrefs.contains("chapter1.xhtml"))
        assertTrue(elements.filterIsInstance<ChapterElement.Text>().any { it.content.contains("OPS Heading") })
        assertTrue(elements.filterIsInstance<ChapterElement.Text>().any { it.content.contains("OPS Paragraph") })
    }

    @Test
    fun parseChapter_resolvesNestedImagePaths() {
        val bookFolder = createBookFolder(
            bookId = "parser-images-${UUID.randomUUID()}",
            containerPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            chapterHref = "Text/ch1.xhtml",
            chapterEntryPath = "OEBPS/Text/ch1.xhtml",
            title = "Image Book",
            body = """
                <h1>Image Heading</h1>
                <p>Before Image</p>
                <img src="../Images/pixel.png" />
                <p>After Image</p>
            """.trimIndent(),
            extraEntries = listOf(
                EpubExtraEntry(
                    path = "OEBPS/Images/pixel.png",
                    bytes = tinyPngBytes(),
                    stored = false,
                ),
            ),
        )

        val book = requireNotNull(parser.reparseBook(bookFolder))
        val elements = parser.parseChapter(book.rootPath, requireNotNull(book.spineHrefs.firstOrNull()))
        val image = elements.filterIsInstance<ChapterElement.Image>().single()

        assertTrue(elements.filterIsInstance<ChapterElement.Text>().any { it.content.contains("Before Image") })
        assertTrue(elements.filterIsInstance<ChapterElement.Text>().any { it.content.contains("After Image") })
        assertTrue(image.data.isNotEmpty())
        assertArrayEquals(tinyPngBytes(), image.data)
    }

    private fun createBookFolder(
        bookId: String,
        containerPath: String,
        tocPath: String,
        chapterHref: String,
        chapterEntryPath: String,
        title: String,
        body: String,
        extraEntries: List<EpubExtraEntry> = emptyList(),
    ): File {
        val folder = File(booksDir, bookId).apply { mkdirs() }
        val epubFile = File(folder, EPUB_ARCHIVE_FILE_NAME)

        ZipOutputStream(FileOutputStream(epubFile)).use { zip ->
            addStoredEntry(zip, "mimetype", "application/epub+zip")
            addEntry(
                zip,
                "META-INF/container.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="$containerPath" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent(),
            )
            addEntry(
                zip,
                containerPath,
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title>
                    <dc:creator>Blue Waves</dc:creator>
                    <dc:identifier id="BookId">urn:uuid:$bookId</dc:identifier>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="${tocPath.substringAfterLast("/")}" media-type="application/x-dtbncx+xml"/>
                    <item id="chapter1" href="$chapterHref" media-type="application/xhtml+xml"/>
                    ${extraEntries.joinToString("\n") { extra ->
                        """<item id="${extra.path.substringAfterLast('/').substringBeforeLast('.')}" href="${relativeToContainer(containerPath, extra.path)}" media-type="${mediaTypeFor(extra.path)}"/>"""
                    }}
                  </manifest>
                  <spine toc="ncx">
                    <itemref idref="chapter1"/>
                  </spine>
                </package>
                """.trimIndent(),
            )
            addEntry(
                zip,
                tocPath,
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <head/>
                  <docTitle><text>$title</text></docTitle>
                  <navMap>
                    <navPoint id="navPoint-1" playOrder="1">
                      <navLabel><text>Chapter 1</text></navLabel>
                      <content src="$chapterHref"/>
                    </navPoint>
                  </navMap>
                </ncx>
                """.trimIndent(),
            )
            addEntry(
                zip,
                chapterEntryPath,
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>$title</title></head>
                  <body>$body</body>
                </html>
                """.trimIndent(),
            )
            extraEntries.forEach { entry ->
                addBinaryEntry(zip, entry.path, entry.bytes, entry.stored)
            }
        }

        return folder
    }

    private fun addStoredEntry(zip: ZipOutputStream, name: String, content: String) {
        addBinaryEntry(zip, name, content.toByteArray(StandardCharsets.UTF_8), stored = true)
    }

    private fun addEntry(zip: ZipOutputStream, name: String, content: String) {
        addBinaryEntry(zip, name, content.toByteArray(StandardCharsets.UTF_8), stored = false)
    }

    private fun addBinaryEntry(zip: ZipOutputStream, name: String, bytes: ByteArray, stored: Boolean) {
        val entry = ZipEntry(name).apply {
            if (stored) {
                val crc = CRC32().apply { update(bytes) }
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                this.crc = crc.value
            }
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun relativeToContainer(containerPath: String, targetPath: String): String {
        val containerDir = containerPath.substringBeforeLast("/", "")
        return if (containerDir.isEmpty()) {
            targetPath
        } else {
            targetPath.removePrefix("$containerDir/")
        }
    }

    private fun mediaTypeFor(path: String): String {
        return when (path.substringAfterLast(".").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            else -> "application/octet-stream"
        }
    }

    private fun tinyPngBytes(): ByteArray {
        return byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(),
            0x00, 0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xDD.toByte(),
            0x8D.toByte(), 0xB1.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49,
            0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
    }

    private data class EpubExtraEntry(
        val path: String,
        val bytes: ByteArray,
        val stored: Boolean,
    )
}
