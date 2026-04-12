package com.epubreader.data.parser

import android.net.Uri
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.TocItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        val books = parser.scanBooks()

        assertEquals(1, books.size)
        assertEquals("cached-book", books.first().id)
        assertEquals("Cached Title", books.first().title)
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

    private fun createMinimalEpub(name: String): File {
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
        return file
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
}
