package com.epubreader.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
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
class AppNavigationLibraryFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val settingsManager by lazy { SettingsManager(composeRule.activity) }
    private val parser by lazy { EpubParser(composeRule.activity) }
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
    fun createRenameMoveAndDeleteFolderFlow_preservesBooks() = runBlocking {
        createLibraryBook("Flow Book One")
        createLibraryBook("Flow Book Two")
        launchAppShell()

        waitUntilDisplayed("Flow Book One")
        waitUntilDisplayed("Flow Book Two")

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithContentDescription("New Folder").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Flow Shelf")
        composeRule.onNodeWithText("Create").performClick()
        waitUntilContentDescriptionExists("Rename Flow Shelf")

        composeRule.onNodeWithContentDescription("Rename Flow Shelf").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Flow Shelf Renamed")
        composeRule.onNodeWithText("Rename").performClick()
        waitUntilContentDescriptionExists("Rename Flow Shelf Renamed")

        composeRule.onNodeWithContentDescription("Folder My Library").performClick()
        waitUntilDisplayed("Flow Book One")

        composeRule.onNodeWithText("Flow Book One").performTouchInput { longClick() }
        waitUntilDisplayed("1 Selected")
        composeRule.onNodeWithContentDescription("Edit Selected Book").assertIsEnabled()
        composeRule.onNodeWithText("Flow Book Two").performClick()
        waitUntilDisplayed("2 Selected")
        composeRule.onNodeWithContentDescription("Edit Selected Book").assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("Move Selected Books").performClick()
        waitUntilDisplayed("Move To")
        composeRule.onNodeWithContentDescription("Folder Flow Shelf Renamed").performClick()

        waitUntilContentDescriptionExists("Menu")
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithContentDescription("Folder Flow Shelf Renamed").performClick()
        waitUntilDisplayed("Flow Book One")
        waitUntilDisplayed("Flow Book Two")

        waitUntilContentDescriptionExists("Menu")
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithContentDescription("Selection Mode").performClick()
        composeRule.onNodeWithContentDescription("Folder Flow Shelf Renamed").performClick()
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete All").performClick()

        waitUntilContentDescriptionExists("Folder My Library")
        composeRule.onNodeWithContentDescription("Folder My Library").performClick()
        waitUntilDisplayed("Flow Book One")
        waitUntilDisplayed("Flow Book Two")
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

    private fun createLibraryBook(title: String): EpubBook {
        val bookId = "app-shell-${UUID.randomUUID()}"
        val bookFolder = File(booksDir, bookId).apply { mkdirs() }
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
                    <dc:title>$title</dc:title>
                    <dc:creator>Blue Waves</dc:creator>
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
                """
                <?xml version="1.0" encoding="UTF-8"?>
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
                """
                <?xml version="1.0" encoding="UTF-8"?>
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
