package com.epubreader.feature.reader

import android.content.ClipboardManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.internal.runtime.epub.ReaderChapterSelectionHost
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectableTextSection
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionController
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionHandle
import com.epubreader.feature.reader.internal.runtime.epub.buildReaderChapterSections
import com.epubreader.feature.reader.internal.runtime.epub.buildReaderSelectionDocument
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSelectionParagraphBoundaryBehaviorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun draggingEndHandleToTheNextParagraphLine_keepsThePreviousParagraphSelected() {
        lateinit var selectionController: ReaderSelectionController
        val firstParagraph = "The tyrant, in comparison, was moving much faster."
        val secondParagraph = "Pushing Hero back with a deadly swipe of his lower arms, he"
        val clipboardManager = requireClipboardManager()
        val settings = GlobalSettings(
            selectableText = true,
            fontSize = 48,
        )
        val chapterElements = listOf(
            ChapterElement.Text(firstParagraph, id = "p1"),
            ChapterElement.Text(secondParagraph, id = "p2"),
        )

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("selection_surface"),
                    ) {
                        val listState = rememberLazyListState()
                        val sections = buildReaderChapterSections(chapterElements)
                        val selectionDocument = buildReaderSelectionDocument(sections)
                        ReaderChapterSelectionHost(
                            settings = settings,
                            themeColors = getThemeColors("light"),
                            listState = listState,
                            selectionDocument = selectionDocument,
                            selectionSessionEpoch = 0,
                            onSelectionActiveChange = { _, _ -> },
                        ) { controller ->
                            SideEffect {
                                selectionController = controller
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 80.dp),
                            ) {
                                items(selectionDocument.sections, key = { it.sectionId }) { section ->
                                    ReaderSelectableTextSection(
                                        section = section,
                                        settings = settings,
                                        themeColors = getThemeColors("light"),
                                        selectionController = controller,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader_compose_text_section", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val sectionInteraction = composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true)
        val sectionNode = sectionInteraction
            .fetchSemanticsNode()
        val sectionBounds = sectionNode.boundsInRoot

        sectionInteraction.performTouchInput {
            longClick(
                Offset(
                    x = 60f,
                    y = 32f,
                ),
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("text_selection_action_bar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader_selection_handle_end", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val endHandleBounds = composeRule.onNodeWithTag("reader_selection_handle_end", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val endHandleCenterInRoot = Offset(
            x = (endHandleBounds.left + endHandleBounds.right) / 2f,
            y = (endHandleBounds.top + endHandleBounds.bottom) / 2f,
        )
        val hostOriginInRoot = composeRule.runOnIdle {
            selectionController.hostOriginInRoot
        }
        val endHandleCenterInHost = Offset(
            x = endHandleCenterInRoot.x - hostOriginInRoot.x,
            y = endHandleCenterInRoot.y - hostOriginInRoot.y,
        )
        val targetPointInHost = Offset(
            x = (sectionBounds.left + (sectionBounds.width * 0.76f)) - hostOriginInRoot.x,
            y = (sectionBounds.top + (sectionBounds.height * 0.73f)) - hostOriginInRoot.y,
        )
        val totalDelta = Offset(
            x = targetPointInHost.x - endHandleCenterInHost.x,
            y = targetPointInHost.y - endHandleCenterInHost.y,
        )

        composeRule.runOnUiThread {
            selectionController.startHandleDrag(
                handle = ReaderSelectionHandle.End,
                pointerInHost = endHandleCenterInHost,
            )
            repeat(8) {
                selectionController.updateHandleDrag(
                    Offset(
                        x = endHandleCenterInHost.x + (totalDelta.x * (it + 1) / 8f),
                        y = endHandleCenterInHost.y + (totalDelta.y * (it + 1) / 8f),
                    ),
                )
            }
            selectionController.finishHandleDrag()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Copy", useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) {
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString()
                ?.isNotBlank() == true
        }

        val selectedText = clipboardManager.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(composeRule.activity)
            ?.toString()
            .orEmpty()

        assertTrue(
            "Expected the dragged end handle to keep the last paragraph line selected, but copied '$selectedText'.",
            selectedText.contains("moving much faster."),
        )
        assertFalse(
            "Expected the paragraph-boundary drag to stop before the next paragraph text, but copied '$selectedText'.",
            selectedText.contains("Pushing Hero back"),
        )
    }

    private fun requireClipboardManager(): ClipboardManager {
        return composeRule.activity.getSystemService(ClipboardManager::class.java)
            ?: error("ClipboardManager was not available")
    }
}
