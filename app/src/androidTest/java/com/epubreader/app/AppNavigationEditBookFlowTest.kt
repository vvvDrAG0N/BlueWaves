package com.epubreader.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
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
class AppNavigationEditBookFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val settingsManager by lazy { SettingsManager(composeRule.activity) }
    private val parser by lazy { EpubParser.create(composeRule.activity) }
    private val booksDir by lazy { File(composeRule.activity.cacheDir, "books") }
    private val createdBookIds = mutableListOf<String>()
    private val currentVersionCode by lazy {
        composeRule.activity.packageManager
            .getPackageInfo(composeRule.activity.packageName, 0)
            .longVersionCode
            .toInt()
    }

    @Before
    fun setUp() = runBlocking {
        booksDir.deleteRecursively()
        booksDir.mkdirs()
        resetSettings()
    }

    @After
    fun tearDown() = runBlocking {
        createdBookIds.forEach { bookId ->
            settingsManager.deleteBookData(bookId)
        }
        booksDir.deleteRecursively()
        resetSettings()
    }

    @Test
    fun editBookFlow_updatesLibraryMetadataAndChapterCount() = runBlocking {
        val book = createLibraryBook("Flow Edit Book", "Flow Author")
        launchAppShell()

        waitUntilDisplayed("Flow Edit Book")
        composeRule.onNodeWithText("Flow Edit Book").performTouchInput { longClick() }
        waitUntilContentDescriptionExists("Edit Selected Book")
        composeRule.onNodeWithContentDescription("Edit Selected Book").performClick()
        waitUntilDisplayed("Edit Book")

        composeRule.onNodeWithTag("edit-book-title").performTextReplacement("Edited Flow Book")
        composeRule.onNodeWithTag("edit-book-author").performTextReplacement("Edited Flow Author")
        composeRule.onNodeWithText("Chapters").performClick()
        composeRule.onNodeWithTag("edit-book-action-add-text").performClick()
        composeRule.onNodeWithTag("edit-book-add-title").performTextReplacement("Bonus Chapter")
        composeRule.onNodeWithTag("edit-book-add-body").performTextReplacement("Bonus chapter body.")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithTag("edit-book-save").performClick()

        waitUntilDisplayed("Edited Flow Book")
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runBlocking {
                parser.scanBooks().any { scanned ->
                    scanned.id == book.id &&
                        scanned.title == "Edited Flow Book" &&
                        scanned.author == "Edited Flow Author" &&
                        scanned.spineHrefs.size == 2
                }
            }
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

    private fun waitUntilContentDescriptionExists(description: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun createLibraryBook(
        title: String,
        author: String,
    ): EpubBook {
        val bookId = "app-edit-${UUID.randomUUID()}"
        val bookFolder = File(booksDir, bookId).apply { mkdirs() }
        val epubFile = File(bookFolder, "book.epub")

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
                """<?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title>
                    <dc:creator>$author</dc:creator>
                    <dc:identifier id="BookId">urn:uuid:$bookId</dc:identifier>
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
                """<?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <head/>
                  <docTitle><text>$title</text></docTitle>
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
                """<?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>$title</title></head>
                  <body>
                    <h1>$title</h1>
                    <p>$title paragraph</p>
                  </body>
                </html>
                """.trimIndent(),
            )
        }

        val book = requireNotNull(parser.reparseBook(bookFolder))
        createdBookIds += book.id
        return book
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
