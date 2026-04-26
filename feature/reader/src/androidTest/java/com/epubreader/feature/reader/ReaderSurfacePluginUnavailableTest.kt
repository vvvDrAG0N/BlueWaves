package com.epubreader.feature.reader

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.core.model.BookFormat
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReaderSurfacePluginUnavailableTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val parser by lazy { EpubParser.create(composeRule.activity) }
    private val settingsManager by lazy { SettingsManager(composeRule.activity) }
    private val createdFolders = mutableListOf<File>()

    @After
    fun tearDown() {
        createdFolders.forEach(File::deleteRecursively)
    }

    @Test
    fun missingBook_showsUnavailableMessageAndBackPath() {
        openReaderSurface(bookId = "missing-${UUID.randomUUID()}")

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("This book is no longer available.").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("This book is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithText("Back to Library").assertIsDisplayed()
    }

    @Test
    fun pdfBook_showsDisabledShellMessageAndBackPath() {
        val bookId = createStoredPdfBook(prefix = "reader-surface-pdf").name

        openReaderSurface(bookId = bookId)

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("PDF reading remains disabled in the active shell.").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("PDF reading remains disabled in the active shell.").assertIsDisplayed()
        composeRule.onNodeWithText("Back to Library").assertIsDisplayed()
    }

    private fun openReaderSurface(bookId: String) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderSurfacePlugin.Render(
                        route = ReaderRoute(bookId = bookId),
                        dependencies = ReaderDependencies(
                            parser = parser,
                            settingsManager = settingsManager,
                        ),
                        onEvent = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun createStoredPdfBook(prefix: String): File {
        val bookId = "$prefix-${UUID.randomUUID()}"
        val bookFolder = File(composeRule.activity.cacheDir, "books/$bookId").apply { mkdirs() }
        File(bookFolder, "source.pdf").writeBytes(byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte()))
        File(bookFolder, "metadata.json").writeText(
            JSONObject().apply {
                put("id", bookId)
                put("title", "PDF Test")
                put("author", "PDF Document")
                put("coverPath", JSONObject.NULL)
                put("originalCoverPath", JSONObject.NULL)
                put("currentCoverPath", JSONObject.NULL)
                put("rootPath", bookFolder.absolutePath)
                put("format", BookFormat.PDF.name)
                put("sourceFormat", BookFormat.PDF.name)
                put("conversionStatus", "NONE")
                put("hasPdfFallback", true)
                put("conversionCompletedPages", 0)
                put("conversionTotalPages", 1)
                put("dateAdded", System.currentTimeMillis())
                put("lastRead", 0L)
                put("toc", JSONArray())
                put("spineHrefs", JSONArray())
                put("pageCount", 1)
            }.toString(),
        )
        createdFolders += bookFolder
        return bookFolder
    }
}
