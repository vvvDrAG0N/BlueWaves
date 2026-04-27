package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderChapterSelectionContentReplacementTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun replacementContent_withoutSelectionSessionEpoch_stillAllowsSelectingNewText() {
        val clipboardManager = composeRule.requireClipboardManager()
        val chapterElements = mutableStateOf(
            listOf(
                ChapterElement.Text("Scholarship", id = "p1"),
            ),
        )

        setReplacementContent(chapterElements)
        composeRule.activateSelection()

        composeRule.runOnUiThread {
            chapterElements.value = listOf(
                ChapterElement.Text("Nextchapter", id = "p2"),
            )
        }
        composeRule.waitForIdle()

        composeRule.activateSelection()
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
        val clipboardManager = composeRule.requireClipboardManager()
        val chapterElements = mutableStateOf(
            listOf(
                ChapterElement.Text("Scholarship", id = "p1"),
            ),
        )

        setReplacementContent(chapterElements)

        composeRule.runOnUiThread {
            chapterElements.value = listOf(
                ChapterElement.Text("Nextchapter", id = "p2"),
            )
        }
        composeRule.waitForIdle()

        composeRule.activateSelection()
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

    private fun setReplacementContent(chapterElements: State<List<ChapterElement>>) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("selection_surface"),
                    ) {
                        val currentChapterElements = chapterElements.value
                        ReaderChapterContent(
                            settings = GlobalSettings(selectableText = true),
                            themeColors = getThemeColors("light"),
                            listState = rememberLazyListState(),
                            chapterSections = buildReaderChapterSections(currentChapterElements),
                            isLoadingChapter = false,
                            currentChapterIndex = 0,
                        )
                    }
                }
            }
        }
    }

}
