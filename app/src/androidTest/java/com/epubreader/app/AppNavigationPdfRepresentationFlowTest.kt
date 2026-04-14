package com.epubreader.app

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
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import com.epubreader.testing.PdfAndroidTestFixtures
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppNavigationPdfRepresentationFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val settingsManager by lazy { SettingsManager(composeRule.activity) }
    private val parser by lazy { EpubParser(composeRule.activity) }
    private val booksDir by lazy { File(composeRule.activity.cacheDir, "books") }
    private val createdPdfs = mutableListOf<File>()
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
        booksDir.deleteRecursively()
        createdPdfs.forEach(File::delete)
        resetSettings()
    }

    @Test
    fun convertedPdf_switchesBetweenRepresentations_withoutMixingProgress() {
        runBlocking {
            val pdfFile = PdfAndroidTestFixtures.createTwoPageTextPdf(
                context = composeRule.activity,
                namePrefix = "app-navigation-pdf",
            )
            createdPdfs += pdfFile
            val importedBook = requireNotNull(parser.parseAndExtract(android.net.Uri.fromFile(pdfFile)))

            settingsManager.saveBookProgress(
                importedBook.id,
                BookProgress(
                    scrollIndex = 0,
                    scrollOffset = 0,
                    lastChapterHref = "pages/page-0002.xhtml",
                ),
                representation = BookRepresentation.EPUB,
            )
            settingsManager.saveBookProgress(
                importedBook.id,
                BookProgress(
                    scrollIndex = 1,
                    scrollOffset = 0,
                    lastChapterHref = null,
                ),
                representation = BookRepresentation.PDF,
            )

            launchAppShell()
            waitUntilDisplayed(importedBook.title, timeoutMillis = 60_000)

            composeRule.onNodeWithText(importedBook.title).performClick()

            waitUntilTagExists("reader_controls_overlay", timeoutMillis = 60_000)
            composeRule.onNodeWithTag("reader_controls_overlay", useUnmergedTree = true).performClick()
            waitUntilTagExists("reader_top_bar", timeoutMillis = 15_000)
            waitUntilTextExists("Page 2", timeoutMillis = 15_000)
            composeRule.onNodeWithContentDescription("Open original PDF").assertIsDisplayed()

            composeRule.onNodeWithContentDescription("Open original PDF").performClick()

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
            waitUntilTextExists("Page 2", timeoutMillis = 15_000)
            composeRule.onNodeWithContentDescription("Open original PDF").assertIsDisplayed()
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

    private fun waitUntilTextExists(text: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
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
}
