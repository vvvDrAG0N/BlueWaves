package com.epubreader.app

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.ConversionStatus
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.parser.GENERATED_EPUB_FILE_NAME
import com.epubreader.data.settings.SettingsManager
import com.epubreader.testing.PdfAndroidTestFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import com.epubreader.data.parser.saveBookMetadata

@RunWith(AndroidJUnit4::class)
class AppNavigationPdfRepresentationFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val settingsManager by lazy { SettingsManager(composeRule.activity) }
    private val parser by lazy { EpubParser(composeRule.activity) }
    private val booksDir by lazy { File(composeRule.activity.cacheDir, "books") }
    private val createdPdfs = mutableListOf<File>()
    private val createdEpubs = mutableListOf<File>()
    private val currentVersionCode by lazy {
        composeRule.activity.packageManager
            .getPackageInfo(composeRule.activity.packageName, 0)
            .longVersionCode
            .toInt()
    }
    private val pdfSupportDisabledMessage =
        "PDF support is temporarily disabled while we prepare a safer refactor."

    @Before
    fun setUp() = runBlocking {
        booksDir.deleteRecursively()
        booksDir.mkdirs()
        resetSettings()
    }

    @After
    fun tearDown() = runBlocking {
        booksDir.deleteRecursively()
        createdPdfs.forEach(File::delete)
        createdEpubs.forEach(File::delete)
        resetSettings()
    }

    @Test
    @Ignore("PDF app-shell support is temporarily disabled; retained for the planned refactor.")
    fun convertedPdf_switchesBetweenRepresentations_withoutMixingProgress() {
        runBlocking {
            val pdfFile = PdfAndroidTestFixtures.createTwoPageTextPdf(
                context = composeRule.activity,
                namePrefix = "app-navigation-pdf",
            )
            createdPdfs += pdfFile
            val importedBook = requireNotNull(parser.parseAndExtract(android.net.Uri.fromFile(pdfFile)))
            val convertedBook = requireNotNull(parser.convertStoredPdfForBook(importedBook.id))

            settingsManager.saveBookProgress(
                convertedBook.id,
                BookProgress(
                    scrollIndex = 0,
                    scrollOffset = 0,
                    lastChapterHref = "sections/section-0001.xhtml",
                ),
                representation = BookRepresentation.EPUB,
            )
            settingsManager.saveBookProgress(
                convertedBook.id,
                BookProgress(
                    scrollIndex = 1,
                    scrollOffset = 0,
                    lastChapterHref = null,
                ),
                representation = BookRepresentation.PDF,
            )

            launchAppShell()
            waitUntilDisplayed(convertedBook.title, timeoutMillis = 60_000)

            composeRule.onNodeWithText(convertedBook.title).performClick()

            waitUntilTagExists("pdf_reader_surface", timeoutMillis = 30_000)
            waitUntilContentDescriptionDisplayed("PDF page 2", timeoutMillis = 30_000)
            composeRule.onNodeWithTag("pdf_reader_surface", useUnmergedTree = true).performClick()
            waitUntilTagExists("pdf_reader_top_bar", timeoutMillis = 15_000)
            waitUntilTextExists("EPUB", timeoutMillis = 15_000)
            composeRule.onNodeWithContentDescription("PDF page 2").assertIsDisplayed()

            composeRule.onNodeWithText("EPUB").performClick()

            waitUntilTagExists("reader_controls_overlay", timeoutMillis = 30_000)
            composeRule.onNodeWithTag("reader_controls_overlay", useUnmergedTree = true).performClick()
            waitUntilTagExists("reader_top_bar", timeoutMillis = 15_000)
            waitUntilTextExists("Section 1", timeoutMillis = 15_000)
            waitUntilTextExists(
                "Blue Waves PDF instrumentation page 1",
                timeoutMillis = 15_000,
                substring = true,
            )
            composeRule.onNodeWithContentDescription("Open original PDF").assertIsDisplayed()

            composeRule.onNodeWithContentDescription("Open original PDF").performClick()

            waitUntilTagExists("pdf_reader_surface", timeoutMillis = 30_000)
            waitUntilContentDescriptionDisplayed("PDF page 2", timeoutMillis = 30_000)
        }
    }

    @Test
    @Ignore("PDF app-shell support is temporarily disabled; retained for the planned refactor.")
    fun switchingToOriginalPdf_savesLatestEpubProgressImmediately() {
        runBlocking {
            val manyPagePdf = PdfAndroidTestFixtures.createMultiPageTextPdf(
                context = composeRule.activity,
                namePrefix = "app-navigation-pdf-immediate-switch-many-pages",
                pageCount = 13,
            )
            createdPdfs += manyPagePdf
            val manyPageImported = requireNotNull(parser.parseAndExtract(Uri.fromFile(manyPagePdf)))
            val manyPageConverted = requireNotNull(parser.convertStoredPdfForBook(manyPageImported.id))

            settingsManager.saveBookProgress(
                manyPageConverted.id,
                BookProgress(
                    scrollIndex = 0,
                    scrollOffset = 0,
                    lastChapterHref = "sections/section-0001.xhtml",
                ),
                representation = BookRepresentation.EPUB,
            )

            launchAppShell()
            waitUntilDisplayed(manyPageConverted.title, timeoutMillis = 60_000)

            composeRule.onNodeWithText(manyPageConverted.title).performClick()

            waitUntilTagExists("pdf_reader_surface", timeoutMillis = 30_000)
            composeRule.onNodeWithTag("pdf_reader_surface", useUnmergedTree = true).performClick()
            waitUntilTagExists("pdf_reader_top_bar", timeoutMillis = 15_000)
            waitUntilTextExists("EPUB", timeoutMillis = 15_000)
            composeRule.onNodeWithText("EPUB").performClick()

            waitUntilTagExists("reader_controls_overlay", timeoutMillis = 60_000)
            composeRule.onNodeWithTag("reader_controls_overlay", useUnmergedTree = true).performClick()
            waitUntilTagExists("reader_top_bar", timeoutMillis = 15_000)
            waitUntilContentDescriptionDisplayed("Next Section", timeoutMillis = 15_000)
            waitUntilContentDescriptionDisplayed("Open original PDF", timeoutMillis = 15_000)

            composeRule.onNodeWithContentDescription("Next Section").performClick()
            composeRule.onNodeWithContentDescription("Open original PDF").performClick()

            waitUntilTagExists("pdf_reader_surface", timeoutMillis = 30_000)

            val epubProgress = settingsManager
                .getBookProgress(manyPageConverted.id, BookRepresentation.EPUB)
                .first()

            assertEquals("sections/section-0002.xhtml", epubProgress.lastChapterHref)
        }
    }

    @Test
    @Ignore("PDF app-shell support is temporarily disabled; retained for the planned refactor.")
    fun queuedPdf_staysInPdfReader_whenBackgroundConversionFinishes() {
        runBlocking {
            val pdfFile = PdfAndroidTestFixtures.createTwoPageTextPdf(
                context = composeRule.activity,
                namePrefix = "app-navigation-open-queued-pdf",
            )
            createdPdfs += pdfFile
            val importedBook = requireNotNull(parser.parseAndExtract(android.net.Uri.fromFile(pdfFile)))

            launchAppShell()
            waitUntilDisplayed(importedBook.title, timeoutMillis = 60_000)

            composeRule.onNodeWithText(importedBook.title).performClick()

            waitUntilTagExists("pdf_reader_surface", timeoutMillis = 30_000)
            waitUntilContentDescriptionDisplayed("PDF page 1", timeoutMillis = 30_000)
            composeRule.onNodeWithTag("pdf_reader_surface", useUnmergedTree = true).performClick()
            waitUntilTagExists("pdf_reader_top_bar", timeoutMillis = 15_000)

            val queued = requireNotNull(parser.retryPdfConversion(importedBook))
            waitUntilTextExists("EPUB", timeoutMillis = 60_000)

            composeRule.onNodeWithTag("pdf_reader_top_bar", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithText("EPUB").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("PDF page 1").assertIsDisplayed()
            assertEquals(BookRepresentation.PDF, queued.activeRepresentation)
        }
    }

    @Test
    @Ignore("PDF app-shell support is temporarily disabled; retained for the planned refactor.")
    fun corruptConvertedEpub_opensRawPdfFallbackFromLibrary() {
        runBlocking {
            val pdfFile = PdfAndroidTestFixtures.createTwoPageTextPdf(
                context = composeRule.activity,
                namePrefix = "app-navigation-corrupt-converted-pdf",
            )
            createdPdfs += pdfFile
            val importedBook = requireNotNull(parser.parseAndExtract(Uri.fromFile(pdfFile)))
            val convertedBook = requireNotNull(parser.convertStoredPdfForBook(importedBook.id))
            val bookFolder = File(convertedBook.rootPath)

            createUnreadableGeneratedPdfFallbackEpub(
                outputFile = File(bookFolder, GENERATED_EPUB_FILE_NAME),
                title = convertedBook.title,
                author = convertedBook.author,
            )
            saveBookMetadata(
                bookFolder,
                convertedBook.copy(
                    format = BookFormat.EPUB,
                    conversionStatus = ConversionStatus.READY,
                ),
            )

            launchAppShell()
            waitUntilDisplayed(convertedBook.title, timeoutMillis = 60_000)

            composeRule.onNodeWithText(convertedBook.title).performClick()

            waitUntilTagExists("pdf_reader_surface", timeoutMillis = 30_000)
            waitUntilContentDescriptionDisplayed("PDF page 1", timeoutMillis = 30_000)
        }
    }

    @Test
    fun legacyPdfEntry_staysInLibrary_andShowsDeprecationMessage() {
        runBlocking {
            val pdfFile = PdfAndroidTestFixtures.createTwoPageTextPdf(
                context = composeRule.activity,
                namePrefix = "app-navigation-disabled-pdf",
            )
            createdPdfs += pdfFile
            val importedBook = requireNotNull(parser.parseAndExtract(Uri.fromFile(pdfFile)))

            launchAppShell()
            waitUntilDisplayed(importedBook.title, timeoutMillis = 60_000)

            composeRule.onNodeWithText(importedBook.title).performClick()

            waitUntilTextExists(pdfSupportDisabledMessage, timeoutMillis = 15_000)
            composeRule.onNodeWithText(importedBook.title).assertIsDisplayed()
        }
    }

    @Test
    fun nativeEpub_opensWhilePdfConversionIsRunning() {
        runBlocking {
            val pdfFile = PdfAndroidTestFixtures.createMultiPageTextPdf(
                context = composeRule.activity,
                namePrefix = "app-navigation-pdf-background-conversion",
                pageCount = 13,
            )
            createdPdfs += pdfFile
            val importedPdf = requireNotNull(parser.parseAndExtract(Uri.fromFile(pdfFile)))
            requireNotNull(parser.retryPdfConversion(importedPdf))

            val epubFile = createMinimalEpub("background-conversion-epub")
            createdEpubs += epubFile
            val epubBook = requireNotNull(parser.parseAndExtract(Uri.fromFile(epubFile)))

            launchAppShell()
            waitUntilDisplayed(epubBook.title, timeoutMillis = 60_000)

            composeRule.onNodeWithText(epubBook.title).performClick()

            waitUntilTagExists("reader_controls_overlay", timeoutMillis = 30_000)
            waitUntilTextExists("Hello from the EPUB reader.", timeoutMillis = 15_000)
        }
    }

    private suspend fun resetSettings() {
        settingsManager.updateGlobalSettings(
            GlobalSettings(
                firstTime = false,
                lastSeenVersionCode = currentVersionCode,
            ),
        )
    }

    private fun launchAppShell() {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                val globalSettings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
                MaterialTheme {
                    AppNavigation(settingsManager, globalSettings)
                }
            }
        }
    }

    private fun waitUntilDisplayed(text: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onNodeWithText(text).assertIsDisplayed()
                true
            }.getOrElse {
                false
            }
        }
    }

    private fun waitUntilTextExists(
        text: String,
        timeoutMillis: Long,
        substring: Boolean = false,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onAllNodesWithText(
                    text = text,
                    substring = substring,
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun waitUntilTagExists(tag: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun waitUntilContentDescriptionDisplayed(
        description: String,
        timeoutMillis: Long,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onNodeWithContentDescription(description, useUnmergedTree = true)
                    .assertIsDisplayed()
                true
            }.getOrElse {
                false
            }
        }
    }

    private fun createMinimalEpub(namePrefix: String): File {
        val epubFile = File(
            composeRule.activity.cacheDir,
            "$namePrefix-${System.currentTimeMillis()}.epub",
        )
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
                    <dc:title>Background Conversion EPUB</dc:title>
                    <dc:creator>Blue Waves</dc:creator>
                    <dc:identifier id="BookId">urn:uuid:background-conversion-epub</dc:identifier>
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
                  <docTitle><text>Background Conversion EPUB</text></docTitle>
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
                  <body><p>Hello from the EPUB reader.</p></body>
                </html>
                """.trimIndent(),
            )
        }
        return epubFile
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

    private fun createUnreadableGeneratedPdfFallbackEpub(
        outputFile: File,
        title: String,
        author: String,
    ) {
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
                    <dc:identifier id="BookId">urn:uuid:corrupt-converted-book</dc:identifier>
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
}
