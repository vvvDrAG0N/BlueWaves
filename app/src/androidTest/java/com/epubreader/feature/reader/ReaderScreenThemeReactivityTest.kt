package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.ThemePalette
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
class ReaderScreenThemeReactivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val settingsManager by lazy { SettingsManager(composeRule.activity) }
    private val parser by lazy { EpubParser(composeRule.activity) }
    private val createdBookIds = mutableListOf<String>()
    private val createdFolders = mutableListOf<File>()

    @Before
    fun setUp() = runBlocking {
        settingsManager.updateGlobalSettings(GlobalSettings(theme = "light"))
    }

    @After
    fun tearDown() = runBlocking {
        createdBookIds.forEach { bookId ->
            settingsManager.deleteBookData(bookId)
        }
        createdFolders.forEach(File::deleteRecursively)
        settingsManager.updateGlobalSettings(GlobalSettings(theme = "light"))
    }

    @Test
    fun changingThemeUpdatesReaderColorsWithoutReopen() = runBlocking {
        val book = createThemeBook()
        settingsManager.saveBookProgress(
            book.id,
            BookProgress(
                scrollIndex = 0,
                scrollOffset = 0,
                lastChapterHref = "chapter1.xhtml",
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

        waitUntilDisplayed("Theme Paragraph 0")
        assertBackgroundColor(Color.White)

        settingsManager.updateGlobalSettings(GlobalSettings(theme = "sepia"))

        composeRule.waitUntil(10_000) { backgroundMatches(Color(0xFFF4ECD8)) }

        composeRule.onNodeWithText("Theme Paragraph 0").assertIsDisplayed()
        assertBackgroundColor(Color(0xFFF4ECD8))
    }

    @Test
    fun changingToCustomThemeUpdatesReaderColorsWithoutReopen() = runBlocking {
        val book = createThemeBook()
        val customTheme = CustomTheme(
            id = "custom-ocean",
            name = "Ocean",
            palette = ThemePalette(
                primary = 0xFF2A6F97,
                secondary = 0xFF468FAF,
                background = 0xFFF4FAFF,
                surface = 0xFFFFFFFF,
                surfaceVariant = 0xFFD7EAF7,
                outline = 0xFF8AA7BB,
                readerBackground = 0xFFEEF8FF,
                readerForeground = 0xFF10212D,
            ),
        )
        settingsManager.saveBookProgress(
            book.id,
            BookProgress(
                scrollIndex = 0,
                scrollOffset = 0,
                lastChapterHref = "chapter1.xhtml",
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

        waitUntilDisplayed("Theme Paragraph 0")
        assertBackgroundColor(Color.White)

        settingsManager.updateGlobalSettings(
            GlobalSettings(
                theme = customTheme.id,
                customThemes = listOf(customTheme),
            ),
        )

        composeRule.waitUntil(10_000) { backgroundMatches(Color(0xFFEEF8FF)) }

        composeRule.onNodeWithText("Theme Paragraph 0").assertIsDisplayed()
        assertBackgroundColor(Color(0xFFEEF8FF))
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

    private fun assertBackgroundColor(expected: Color) {
        check(backgroundMatches(expected)) {
            "Reader background did not match expected color ${expected.toArgb()}."
        }
    }

    private fun backgroundMatches(expected: Color): Boolean {
        val image = composeRule.onRoot().captureToImage()
        val pixel = image.toPixelMap()[8, 8]
        return pixel.closeTo(expected)
    }

    private fun Color.closeTo(other: Color, tolerance: Float = 0.03f): Boolean {
        return kotlin.math.abs(red - other.red) <= tolerance &&
            kotlin.math.abs(green - other.green) <= tolerance &&
            kotlin.math.abs(blue - other.blue) <= tolerance
    }

    private fun createThemeBook(): EpubBook {
        val rootDir = File(composeRule.activity.cacheDir, "androidTest-books").apply { mkdirs() }
        val bookId = "reader-theme-${UUID.randomUUID()}"
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
                    <dc:title>Theme Test Book</dc:title>
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
                  <docTitle><text>Theme Test Book</text></docTitle>
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
                  <head><title>Theme Test</title></head>
                  <body>
                    <h1>Theme Header</h1>
                    <p>Theme Paragraph 0</p>
                    <p>Theme Paragraph 1</p>
                    <p>Theme Paragraph 2</p>
                  </body>
                </html>
                """.trimIndent(),
            )
        }

        val book = requireNotNull(parser.reparseBook(bookFolder))
        createdBookIds += book.id
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
