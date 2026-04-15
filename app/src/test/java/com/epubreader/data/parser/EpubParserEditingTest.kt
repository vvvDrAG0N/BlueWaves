package com.epubreader.data.parser

import com.epubreader.core.model.BookChapterAddition
import com.epubreader.core.model.BookCoverUpdate
import com.epubreader.core.model.BookEditRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class EpubParserEditingTest {

    @Test
    fun editStoredEpubBook_updatesMetadataCoverAndChapters() {
        val bookFolder = Files.createTempDirectory("edit-book").toFile()
        writeTestEpub(
            bookFolder = bookFolder,
            title = "Original Title",
            author = "Original Author",
            chapters = listOf(
                "chapter1.xhtml" to "Chapter One",
                "chapter2.xhtml" to "Chapter Two",
            ),
        )
        requireNotNull(rebuildBookMetadata(bookFolder))

        val updated = editStoredEpubBook(
            bookFolder = bookFolder,
            request = BookEditRequest(
                title = "Edited Title",
                author = "Edited Author",
                coverUpdate = BookCoverUpdate(
                    fileName = "cover.png",
                    mimeType = "image/png",
                    bytes = TinyPngBytes,
                ),
                deletedChapterHrefs = setOf("chapter1.xhtml"),
                addedChapters = listOf(
                    BookChapterAddition(
                        title = "New Ending",
                        body = "Fresh final chapter body.",
                    ),
                ),
            ),
        )

        assertNotNull(updated)
        updated!!
        assertEquals("Edited Title", updated.title)
        assertEquals("Edited Author", updated.author)
        assertEquals(
            listOf("chapter2.xhtml", "bluewaves/added-chapter-0001.xhtml"),
            updated.spineHrefs,
        )
        assertTrue(updated.toc.last().title.endsWith("New Ending"))
        assertNotNull(updated.coverPath)
        assertTrue(File(updated.coverPath!!).exists())

        ZipFile(File(bookFolder, EPUB_ARCHIVE_FILE_NAME)).use { zip ->
            assertNotNull(zip.getEntry("OEBPS/bluewaves/custom-cover.png"))
            assertNotNull(zip.getEntry("OEBPS/bluewaves/added-chapter-0001.xhtml"))
        }

        ZipFile(File(bookFolder, EPUB_ARCHIVE_FILE_NAME)).use { zip ->
            val chapterXml = zip.getInputStream(
                requireNotNull(zip.getEntry("OEBPS/bluewaves/added-chapter-0001.xhtml")),
            ).bufferedReader().use { it.readText() }
            assertTrue(chapterXml.contains("Fresh final chapter body."))
        }
    }

    @Test
    fun editStoredEpubBook_rejectsDeletingAllChaptersWithoutReplacement() {
        val bookFolder = Files.createTempDirectory("edit-book-empty").toFile()
        writeTestEpub(
            bookFolder = bookFolder,
            title = "Single Chapter",
            author = "Solo Author",
            chapters = listOf("chapter1.xhtml" to "Only Chapter"),
        )
        val original = requireNotNull(rebuildBookMetadata(bookFolder))

        val result = editStoredEpubBook(
            bookFolder = bookFolder,
            request = BookEditRequest(
                title = "Single Chapter",
                author = "Solo Author",
                deletedChapterHrefs = setOf("chapter1.xhtml"),
            ),
        )

        assertNull(result)
        val reloaded = requireNotNull(rebuildBookMetadata(bookFolder))
        assertEquals(original.spineHrefs, reloaded.spineHrefs)
        assertEquals(original.title, reloaded.title)
    }

    private fun writeTestEpub(
        bookFolder: File,
        title: String,
        author: String,
        chapters: List<Pair<String, String>>,
    ) {
        bookFolder.mkdirs()
        val epubFile = File(bookFolder, EPUB_ARCHIVE_FILE_NAME)

        ZipOutputStream(FileOutputStream(epubFile)).use { zip ->
            addStoredEntry(zip, "mimetype", "application/epub+zip")
            addEntry(
                zip,
                "META-INF/container.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
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
                buildContentOpf(
                    title = title,
                    author = author,
                    chapters = chapters,
                ),
            )
            addEntry(
                zip,
                "OEBPS/toc.ncx",
                buildTocNcx(title = title, chapters = chapters),
            )
            chapters.forEachIndexed { index, (href, chapterTitle) ->
                addEntry(
                    zip,
                    "OEBPS/$href",
                    """<?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>$chapterTitle</title></head>
                      <body>
                        <h1>$chapterTitle</h1>
                        <p>Body ${index + 1}</p>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
        }
    }

    private fun buildContentOpf(
        title: String,
        author: String,
        chapters: List<Pair<String, String>>,
    ): String {
        val manifestItems = buildString {
            append("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
            chapters.forEachIndexed { index, (href, _) ->
                append('\n')
                append("""<item id="chapter${index + 1}" href="$href" media-type="application/xhtml+xml"/>""")
            }
        }
        val spineItems = chapters.indices.joinToString("\n") { index ->
            """<itemref idref="chapter${index + 1}"/>"""
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>$title</dc:title>
                <dc:creator>$author</dc:creator>
                <dc:identifier id="BookId">urn:test:${title.replace(' ', '-')}</dc:identifier>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>$manifestItems</manifest>
              <spine toc="ncx">$spineItems</spine>
            </package>
        """.trimIndent()
    }

    private fun buildTocNcx(
        title: String,
        chapters: List<Pair<String, String>>,
    ): String {
        val navPoints = chapters.mapIndexed { index, (href, chapterTitle) ->
            """<navPoint id="navPoint-${index + 1}" playOrder="${index + 1}">
              <navLabel><text>$chapterTitle</text></navLabel>
              <content src="$href"/>
            </navPoint>
            """.trimIndent()
        }.joinToString("\n")

        return """<?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <head/>
              <docTitle><text>$title</text></docTitle>
              <navMap>$navPoints</navMap>
            </ncx>
        """.trimIndent()
    }

    private fun addStoredEntry(
        zip: ZipOutputStream,
        name: String,
        content: String,
    ) {
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

    private fun addEntry(
        zip: ZipOutputStream,
        name: String,
        content: String,
    ) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    companion object {
        private val TinyPngBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO5P0N8AAAAASUVORK5CYII=",
        )
    }
}
