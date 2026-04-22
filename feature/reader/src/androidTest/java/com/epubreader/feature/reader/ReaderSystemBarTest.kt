package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.flow.first
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
class ReaderSystemBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val settingsManager by lazy { SettingsManager(composeRule.activity) }
    private val parser by lazy { EpubParser.create(composeRule.activity) }
    private val createdFolders = mutableListOf<File>()

    @After
    fun tearDown() = runBlocking {
        createdFolders.forEach(File::deleteRecursively)
    }

    @Test
    fun reader_ToggleControls_UpdatesSystemBars() = runBlocking {
        // 1. Setup: Create a book and ensure global showSystemBar is false.
        val book = createTestBook()
        settingsManager.updateGlobalSettings(GlobalSettings(firstTime = false, showSystemBar = false))
        
        // 2. Open Reader
        openReader(book)

        // 3. Initial State: Controls hidden, System bars hidden.
        composeRule.waitUntil(30000) {
            composeRule.onAllNodesWithText("Chapter 1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertSystemBarsVisibility(visible = false)

        // 4. Tap to show controls. 
        composeRule.onNodeWithTag("reader_controls_overlay", useUnmergedTree = true).performClick()
        
        // 5. Controls should be visible (Top bar is a good indicator)
        composeRule.waitUntil(30000) {
            composeRule.onAllNodesWithTag("reader_top_bar", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertSystemBarsVisibility(visible = true)

        // 6. Tap again to hide
        composeRule.onNodeWithTag("reader_controls_overlay", useUnmergedTree = true).performClick()
        
        // 7. System bars should be hidden again
        composeRule.waitUntil(20000) {
            composeRule.onAllNodesWithTag("reader_top_bar", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        assertSystemBarsVisibility(visible = false)
    }

    private fun openReader(book: EpubBook) = runBlocking {
        composeRule.runOnUiThread {
            val settingsManager = SettingsManager(composeRule.activity)
            val globalSettings = runBlocking { settingsManager.globalSettings.first() }
            
            composeRule.activity.setContent {
                MaterialTheme(
                    colorScheme = com.epubreader.appColorScheme(
                        theme = globalSettings.theme,
                        customThemes = globalSettings.customThemes,
                    )
                ) {
                    ReaderScreen(
                        book = book,
                        settingsManager = settingsManager,
                        parser = parser,
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertSystemBarsVisibility(visible: Boolean) {
        val timeout = 5000L
        composeRule.waitUntil(timeout) {
            var success = false
            composeRule.runOnUiThread {
                val activity = composeRule.activity
                val decorView = activity.window.decorView
                if (!decorView.hasFocus()) {
                    decorView.requestFocus()
                }
                val insets = ViewCompat.getRootWindowInsets(decorView)
                val systemUiVisibility = decorView.systemUiVisibility
                
                if (insets != null) {
                    val statusVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars())
                    val isImmersive = (systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0 ||
                                     (systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
                    
                    val reportedMatches = if (visible) statusVisible else !statusVisible
                    val hiddenByFlags = !visible && isImmersive
                    
                    success = reportedMatches || hiddenByFlags
                }
            }
            success
        }
    }

    private fun createTestBook(): EpubBook {
        val rootDir = File(composeRule.activity.cacheDir, "androidTest-books").apply { mkdirs() }
        val bookId = "reader-test-${UUID.randomUUID()}"
        val bookFolder = File(rootDir, bookId).apply { mkdirs() }
        val epubFile = File(bookFolder, "book.epub")

        ZipOutputStream(FileOutputStream(epubFile)).use { zip ->
            addStoredEntry(zip, "mimetype", "application/epub+zip")
            addEntry(zip, "META-INF/container.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent())
            addEntry(zip, "OEBPS/content.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
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
            """.trimIndent())
            addEntry(zip, "OEBPS/toc.ncx", """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <head/><docTitle><text>Test Book</text></docTitle>
                  <navMap>
                    <navPoint id="nav1" playOrder="1"><navLabel><text>Ch 1</text></navLabel><content src="chapter1.xhtml"/></navPoint>
                  </navMap>
                </ncx>
            """.trimIndent())
            addEntry(zip, "OEBPS/chapter1.xhtml", """
                <html xmlns="http://www.w3.org/1999/xhtml"><body><h1>Chapter 1</h1><p>Content</p></body></html>
            """.trimIndent())
        }

        val book = requireNotNull(parser.reparseBook(bookFolder))
        createdFolders += bookFolder
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
