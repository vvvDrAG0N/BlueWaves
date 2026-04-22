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
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
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

@RunWith(AndroidJUnit4::class)
class AppNavigationPdfRepresentationFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val settingsManager by lazy { SettingsManager(composeRule.activity) }
    private val parser by lazy { EpubParser.create(composeRule.activity) }
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
    fun convertedPdf_switchesBetweenRepresentations_withoutMixingProgress() = Unit

    @Test
    @Ignore("PDF app-shell support is temporarily disabled; retained for the planned refactor.")
    fun switchingToOriginalPdf_savesLatestEpubProgressImmediately() = Unit

    @Test
    @Ignore("PDF app-shell support is temporarily disabled; retained for the planned refactor.")
    fun queuedPdf_staysInPdfReader_whenBackgroundConversionFinishes() = Unit

    @Test
    @Ignore("PDF app-shell support is temporarily disabled; retained for the planned refactor.")
    fun corruptConvertedEpub_opensRawPdfFallbackFromLibrary() = Unit

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

            val epubFile = createMinimalTestEpub(composeRule.activity.cacheDir, "background-conversion-epub")
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

}
