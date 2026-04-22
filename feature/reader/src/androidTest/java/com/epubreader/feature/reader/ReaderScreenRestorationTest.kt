package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.EpubBook
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
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
class ReaderScreenRestorationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val settingsManager by lazy { SettingsManager(composeRule.activity) }
    private val parser by lazy { EpubParser.create(composeRule.activity) }
    private val createdBookIds = mutableListOf<String>()
    private val createdFolders = mutableListOf<File>()

    @After
    fun tearDown() = runBlocking {
        createdBookIds.forEach { bookId ->
            settingsManager.deleteBookData(bookId)
        }
        createdFolders.forEach(File::deleteRecursively)
    }

    @Test
    fun reopenRestoresSavedChapterAndScrollPosition() = runBlocking {
        val book = createRestorationBook()

        settingsManager.saveBookProgress(
            book.id,
            BookProgress(
                scrollIndex = 18,
                scrollOffset = 0,
                lastChapterHref = "chapter2.xhtml",
            ),
        )

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderScreen(
                        book = book,
                        settingsManager = settingsManager,
                        parser = parser,
                        onBack = {},
                    )
                }
            }
        }

        waitUntilDisplayed("Chapter 2 Paragraph 18")

        composeRule.onNodeWithText("Chapter 2 Paragraph 18").assertIsDisplayed()
        check(
            composeRule.onAllNodesWithText("Chapter 1 Paragraph 0")
                .fetchSemanticsNodes().isEmpty(),
        ) { "Reader loaded the wrong chapter content." }
    }

    private fun waitUntilDisplayed(text: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            try {
                composeRule.onNodeWithText(text).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun createRestorationBook(): EpubBook {
        val rootDir = File(composeRule.activity.cacheDir, "androidTest-books").apply { mkdirs() }
        val bookId = "reader-restoration-${UUID.randomUUID()}"
        val bookFolder = File(rootDir, bookId).apply { mkdirs() }
        val epubFile = File(bookFolder, "book.epub")

        ZipOutputStream(FileOutputStream(epubFile)).use { zip ->
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
                    <dc:title>Restoration Test Book</dc:title>
                    <dc:creator>Blue Waves</dc:creator>
                    <dc:identifier id="BookId">urn:uuid:$bookId</dc:identifier>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="chapter2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine toc="ncx">
                    <itemref idref="chapter1"/>
                    <itemref idref="chapter2"/>
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
                  <docTitle><text>Restoration Test Book</text></docTitle>
                  <navMap>
                    <navPoint id="navPoint-1" playOrder="1">
                      <navLabel><text>Chapter 1</text></navLabel>
                      <content src="chapter1.xhtml"/>
                    </navPoint>
                    <navPoint id="navPoint-2" playOrder="2">
                      <navLabel><text>Chapter 2</text></navLabel>
                      <content src="chapter2.xhtml"/>
                    </navPoint>
                  </navMap>
                </ncx>
                """.trimIndent(),
            )
            addEntry(zip, "OEBPS/chapter1.xhtml", chapterXhtml("Chapter 1", 40))
            addEntry(zip, "OEBPS/chapter2.xhtml", chapterXhtml("Chapter 2", 40))
        }

        val book = requireNotNull(parser.reparseBook(bookFolder))
        createdBookIds += book.id
        createdFolders += bookFolder
        return book
    }

    private fun chapterXhtml(chapterLabel: String, paragraphCount: Int): String {
        val body = buildString {
            append("<h1>")
            append(chapterLabel)
            append("</h1>")
            repeat(paragraphCount) { index ->
                append("<p>")
                append(chapterLabel)
                append(" Paragraph ")
                append(index)
                append("</p>")
            }
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title>$chapterLabel</title></head>
              <body>$body</body>
            </html>
        """.trimIndent()
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
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }
}
