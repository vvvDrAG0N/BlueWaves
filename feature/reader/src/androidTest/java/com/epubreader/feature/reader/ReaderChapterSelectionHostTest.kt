package com.epubreader.feature.reader

import android.content.ClipboardManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderChapterSelectionHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activeSelection_tappingChapterContentClearsSelection() {
        val selectionActive = mutableStateOf(false)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderSelectionTestSurface(
                        settings = GlobalSettings(selectableText = true),
                        onSelectionActiveChange = { _, isActive ->
                            selectionActive.value = isActive
                        },
                    )
                }
            }
        }

        activateSelection()
        composeRule.waitUntil(5_000) { selectionActive.value }

        composeRule.onNodeWithTag("selection_surface").performTouchInput {
            click(Offset(width - 10f, height - 10f))
        }

        composeRule.waitUntil(5_000) { !selectionActive.value }
        assertFalse(selectionActive.value)
    }

    @Test
    fun selectionActionBar_copyWritesSelectedTextToClipboard() {
        val clipboardManager = requireClipboardManager()

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderSelectionTestSurface(
                        settings = GlobalSettings(selectableText = true),
                    )
                }
            }
        }

        activateSelection()
        composeRule.onNodeWithText("Copy", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5_000) {
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString() == "Scholarship"
        }

        assertEquals(
            "Scholarship",
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString(),
        )
    }

    @Test
    fun selectionActionBar_defineOpensLookupSheet() {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderSelectionTestSurface(
                        settings = GlobalSettings(selectableText = true),
                    )
                }
            }
        }

        activateSelection()
        composeRule.onNodeWithText("Define", useUnmergedTree = true).performClick()

        composeRule.onNodeWithTag("web_lookup_webview", useUnmergedTree = true).assertExists()
    }

    @Test
    fun selectionActionBar_translateOpensLookupSheet() {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderSelectionTestSurface(
                        settings = GlobalSettings(
                            selectableText = true,
                            targetTranslationLanguage = "en",
                        ),
                    )
                }
            }
        }

        activateSelection()
        composeRule.onNodeWithText("Translate", useUnmergedTree = true).performClick()

        composeRule.onNodeWithTag("web_lookup_webview", useUnmergedTree = true).assertExists()
    }

    @Test
    fun activeSelection_showsBothSelectionHandlesImmediately() {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderSelectionTestSurface(
                        settings = GlobalSettings(selectableText = true),
                    )
                }
            }
        }

        activateSelection()

        composeRule.onNodeWithTag("reader_selection_handle_start", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("reader_selection_handle_end", useUnmergedTree = true).assertExists()
    }

    @Test
    fun activeSelection_hidesSelectionHandlesWhenSelectedWordScrollsOffScreen() {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderSelectionTestSurface(
                        settings = GlobalSettings(selectableText = true),
                        chapterElements = listOf(
                            ChapterElement.Text(
                                "Scholarship ".repeat(500).trim(),
                                id = "p1",
                            ),
                        ),
                    )
                }
            }
        }

        activateSelection()

        composeRule.onNodeWithTag("selection_surface").performTouchInput {
            repeat(3) {
                swipe(
                    start = Offset(width * 0.5f, height * 0.75f),
                    end = Offset(width * 0.5f, height * 0.15f),
                    durationMillis = 200,
                )
                advanceEventTime(100)
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader_selection_handle_start", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("reader_selection_handle_end", useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty()
        }

        assertTrue(
            composeRule.onAllNodesWithTag("reader_selection_handle_start", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithTag("reader_selection_handle_end", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun replacementContent_withoutSelectionSessionEpoch_stillAllowsSelectingNewText() {
        val clipboardManager = requireClipboardManager()
        val chapterElements = mutableStateOf(
            listOf(
                ChapterElement.Text("Scholarship", id = "p1"),
            ),
        )

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("selection_surface"),
                    ) {
                        ReaderChapterContent(
                            settings = GlobalSettings(selectableText = true),
                            themeColors = getThemeColors("light"),
                            listState = rememberLazyListState(),
                            chapterElements = chapterElements.value,
                            isLoadingChapter = false,
                            currentChapterIndex = 0,
                        )
                    }
                }
            }
        }

        activateSelection()

        composeRule.runOnUiThread {
            chapterElements.value = listOf(
                ChapterElement.Text("Nextchapter", id = "p2"),
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true).performTouchInput {
            longClick(Offset(60f, 32f))
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("text_selection_action_bar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Copy", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5_000) {
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString() == "Nextchapter"
        }

        assertEquals(
            "Nextchapter",
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString(),
        )
    }

    @Test
    fun replacementContent_whileIdle_stillAllowsSelectingNewText() {
        val clipboardManager = requireClipboardManager()
        val chapterElements = mutableStateOf(
            listOf(
                ChapterElement.Text("Scholarship", id = "p1"),
            ),
        )

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("selection_surface"),
                    ) {
                        ReaderChapterContent(
                            settings = GlobalSettings(selectableText = true),
                            themeColors = getThemeColors("light"),
                            listState = rememberLazyListState(),
                            chapterElements = chapterElements.value,
                            isLoadingChapter = false,
                            currentChapterIndex = 0,
                        )
                    }
                }
            }
        }

        composeRule.runOnUiThread {
            chapterElements.value = listOf(
                ChapterElement.Text("Nextchapter", id = "p2"),
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true).performTouchInput {
            longClick(Offset(60f, 32f))
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("text_selection_action_bar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Copy", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5_000) {
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString() == "Nextchapter"
        }

        assertEquals(
            "Nextchapter",
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString(),
        )
    }

    private fun activateSelection() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true).performTouchInput {
            longClick(Offset(60f, 32f))
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("text_selection_action_bar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodesWithTag("text_selection_action_bar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun requireClipboardManager(): ClipboardManager {
        return composeRule.activity.getSystemService(ClipboardManager::class.java)
            ?: error("ClipboardManager was not available")
    }

    @Composable
    private fun ReaderSelectionTestSurface(
        settings: GlobalSettings,
        onSelectionActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
        chapterElements: List<ChapterElement> = listOf(
            ChapterElement.Text("Scholarship", id = "p1"),
            ChapterElement.Text("Reading keeps the selection lego steady.", id = "p2"),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("selection_surface"),
        ) {
            ReaderChapterContent(
                settings = settings,
                themeColors = getThemeColors("light"),
                listState = rememberLazyListState(),
                chapterElements = chapterElements,
                isLoadingChapter = false,
                currentChapterIndex = 0,
                onSelectionActiveChange = onSelectionActiveChange,
            )
        }
    }
}
