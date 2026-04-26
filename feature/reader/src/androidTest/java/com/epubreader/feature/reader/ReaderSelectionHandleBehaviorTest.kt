package com.epubreader.feature.reader

import android.content.ClipboardManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSelectionHandleBehaviorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomEdgeHandleDrag_nearTheLowerEdgeKeepsTheTearVisibleAndExpandsSelection() {
        lateinit var listState: LazyListState
        lateinit var selectionController: ReaderSelectionController
        val clipboardManager = requireClipboardManager()
        val chapterElements = listOf(
            ChapterElement.Text(
                "Scholarship keeps the selection handle busy near the lower edge. ".repeat(220).trim(),
                id = "p1",
            ),
        )

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .testTag("selection_surface"),
                    ) {
                        val rememberedListState = rememberLazyListState()
                        val sections = remember(chapterElements) {
                            buildReaderChapterSections(chapterElements)
                        }
                        val selectionDocument = remember(sections) {
                            buildReaderSelectionDocument(sections)
                        }
                        SideEffect {
                            listState = rememberedListState
                        }
                        ReaderChapterSelectionHost(
                            settings = GlobalSettings(selectableText = true),
                            themeColors = getThemeColors("light"),
                            listState = rememberedListState,
                            selectionDocument = selectionDocument,
                            selectionSessionEpoch = 0,
                            onSelectionActiveChange = { _, _ -> },
                        ) { controller ->
                            SideEffect {
                                selectionController = controller
                            }
                            LazyColumn(
                                state = rememberedListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 80.dp),
                            ) {
                                items(selectionDocument.sections, key = { it.sectionId }) { section ->
                                    ReaderSelectableTextSection(
                                        section = section,
                                        settings = GlobalSettings(selectableText = true),
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

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true).performTouchInput {
            longClick(
                Offset(
                    x = width * 0.5f,
                    y = height * 0.72f,
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
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            selectionController.startHandleDrag(
                ReaderSelectionHandle.End,
                Offset(140f, 228f),
            )
        }
        repeat(10) {
            composeRule.runOnUiThread {
                selectionController.updateHandleDrag(Offset(140f, 252f))
            }
            Thread.sleep(120)
        }
        composeRule.runOnUiThread {
            selectionController.finishHandleDrag()
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader_selection_handle_end", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Copy", useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) {
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString()
                ?.length
                ?.let { it > "Scholarship".length } == true
        }
        assertTrue(listState.firstVisibleItemIndex >= 0)
    }

    fun raisedStartHandle_doesNotChangeSelectionUntilTheDragReachesTheTextBoundary() {
        lateinit var selectionController: ReaderSelectionController
        lateinit var sectionId: String
        val chapterElements = listOf(
            ChapterElement.Text(
                "Scholarship keeps the raised start handle above the selected line while the text wraps " +
                    "onto multiple rows for a realistic drag test near the boundary.",
                id = "p1",
            ),
        )

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .testTag("selection_surface"),
                    ) {
                        val rememberedListState = rememberLazyListState()
                        val sections = remember(chapterElements) {
                            buildReaderChapterSections(chapterElements)
                        }
                        val selectionDocument = remember(sections) {
                            buildReaderSelectionDocument(sections)
                        }
                        SideEffect {
                            sectionId = selectionDocument.sections.first().sectionId
                        }
                        ReaderChapterSelectionHost(
                            settings = GlobalSettings(selectableText = true),
                            themeColors = getThemeColors("light"),
                            listState = rememberedListState,
                            selectionDocument = selectionDocument,
                            selectionSessionEpoch = 0,
                            onSelectionActiveChange = { _, _ -> },
                        ) { controller ->
                            SideEffect {
                                selectionController = controller
                            }
                            LazyColumn(
                                state = rememberedListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 80.dp),
                            ) {
                                items(selectionDocument.sections, key = { it.sectionId }) { section ->
                                    ReaderSelectableTextSection(
                                        section = section,
                                        settings = GlobalSettings(selectableText = true),
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

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true).performTouchInput {
            longClick(
                Offset(
                    x = width * 0.55f,
                    y = height * 0.72f,
                ),
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader_selection_handle_start", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val initialRange = composeRule.runOnIdle {
            selectionController.highlightRangeForSection(sectionId)
        }

        composeRule.onNodeWithTag("reader_selection_handle_start", useUnmergedTree = true).performTouchInput {
            down(Offset(x = width / 2f, y = height * 0.2f))
            moveBy(Offset(x = 0f, y = height * 0.45f))
            up()
        }
        composeRule.waitForIdle()

        val afterSmallDragRange = composeRule.runOnIdle {
            selectionController.highlightRangeForSection(sectionId)
        }
        assertEquals(initialRange, afterSmallDragRange)

        composeRule.runOnUiThread {
            selectionController.startHandleDrag(
                ReaderSelectionHandle.Start,
                Offset(120f, 136f),
            )
            selectionController.updateHandleDrag(Offset(24f, 88f))
            selectionController.finishHandleDrag()
        }
        composeRule.waitForIdle()

        val afterLargeDragRange = composeRule.runOnIdle {
            selectionController.highlightRangeForSection(sectionId)
        }
        assertNotEquals(initialRange, afterLargeDragRange)
    }

    @Test
    fun endHandleDrag_canShrinkSelectionInsideTheOriginalWord() {
        lateinit var selectionController: ReaderSelectionController
        lateinit var sectionId: String
        val chapterText = "Scholarship"
        val chapterElements = listOf(
            ChapterElement.Text(
                chapterText,
                id = "p1",
            ),
        )

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .testTag("selection_surface"),
                    ) {
                        val rememberedListState = rememberLazyListState()
                        val sections = remember(chapterElements) {
                            buildReaderChapterSections(chapterElements)
                        }
                        val selectionDocument = remember(sections) {
                            buildReaderSelectionDocument(sections)
                        }
                        SideEffect {
                            sectionId = selectionDocument.sections.first().sectionId
                        }
                        ReaderChapterSelectionHost(
                            settings = GlobalSettings(
                                selectableText = true,
                                fontSize = 48,
                            ),
                            themeColors = getThemeColors("light"),
                            listState = rememberedListState,
                            selectionDocument = selectionDocument,
                            selectionSessionEpoch = 0,
                            onSelectionActiveChange = { _, _ -> },
                        ) { controller ->
                            SideEffect {
                                selectionController = controller
                            }
                            LazyColumn(
                                state = rememberedListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 80.dp),
                            ) {
                                items(selectionDocument.sections, key = { it.sectionId }) { section ->
                                    ReaderSelectableTextSection(
                                        section = section,
                                        settings = GlobalSettings(
                                            selectableText = true,
                                            fontSize = 48,
                                        ),
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

        composeRule.waitForIdle()
        val sectionNode = composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true)
            .fetchSemanticsNode()
        val selectionOffset = Offset(
            x = 60f,
            y = sectionNode.size.height * 0.5f,
        )
        composeRule.runOnUiThread {
            selectionController.startSelectionAt(sectionId, selectionOffset)
            selectionController.updateSelectionGesture(sectionId, selectionOffset)
            selectionController.finishSelectionGesture()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader_selection_handle_end", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val hostOriginInRoot = composeRule.runOnIdle {
            selectionController.hostOriginInRoot
        }
        val sectionBounds = composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val endHandleBounds = composeRule.onNodeWithTag("reader_selection_handle_end", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val sectionLeftInHost = sectionBounds.left - hostOriginInRoot.x
        val lineCenterYInHost = ((sectionBounds.top + sectionBounds.bottom) / 2f) - hostOriginInRoot.y
        val endHandleAnchorXInHost = ((endHandleBounds.left + endHandleBounds.right) / 2f) - hostOriginInRoot.x
        val interiorWordXInHost = sectionLeftInHost + ((endHandleAnchorXInHost - sectionLeftInHost) * 0.6f)

        val initialRange = composeRule.runOnIdle {
            selectionController.highlightRangeForSection(sectionId)
        }
        assertEquals(TextRange(0, chapterText.length), initialRange)

        composeRule.runOnUiThread {
            selectionController.startHandleDrag(
                ReaderSelectionHandle.End,
                Offset(
                    x = endHandleAnchorXInHost,
                    y = lineCenterYInHost,
                ),
            )
            selectionController.updateHandleDrag(
                Offset(
                    x = interiorWordXInHost,
                    y = lineCenterYInHost,
                ),
            )
            selectionController.finishHandleDrag()
        }
        composeRule.waitForIdle()

        val afterDragRange = composeRule.runOnIdle {
            selectionController.highlightRangeForSection(sectionId)
        }
        assertTrue(afterDragRange != null)
        assertTrue(afterDragRange!!.start == 0)
        assertTrue(
            "Expected handle drag to shrink the selection inside '$chapterText', but initial=$initialRange afterDrag=$afterDragRange",
            afterDragRange.end in 1 until chapterText.length,
        )
    }

    private fun requireClipboardManager(): ClipboardManager {
        return composeRule.activity.getSystemService(ClipboardManager::class.java)
            ?: error("ClipboardManager was not available")
    }
}
