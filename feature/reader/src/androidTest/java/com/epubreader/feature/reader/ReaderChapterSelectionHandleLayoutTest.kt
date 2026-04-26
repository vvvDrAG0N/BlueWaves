package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.internal.runtime.epub.ReaderChapterSelectionHost
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectableTextSection
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionController
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionHandleSemanticsKey
import com.epubreader.feature.reader.internal.runtime.epub.buildReaderChapterSections
import com.epubreader.feature.reader.internal.runtime.epub.buildReaderSelectionDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderChapterSelectionHandleLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activeSelection_balancesHandleHeightsAndKeepsThemCloseToTheSelectedWord() {
        composeRule.setReaderSelectionContent(
            chapterElements = listOf(
                ChapterElement.Text("Scholarship", id = "p1"),
            ),
        )

        composeRule.activateSelection()
        composeRule.waitForIdle()

        val startSemantics = composeRule
            .onNodeWithTag("reader_selection_handle_start", useUnmergedTree = true)
            .fetchSemanticsNode().config[ReaderSelectionHandleSemanticsKey]
        val endSemantics = composeRule
            .onNodeWithTag("reader_selection_handle_end", useUnmergedTree = true)
            .fetchSemanticsNode().config[ReaderSelectionHandleSemanticsKey]

        assertEquals(startSemantics.touchTargetHeightPx, endSemantics.touchTargetHeightPx, 1f)
        assertTrue(
            "Expected the start-handle knob above its stem, but got $startSemantics",
            startSemantics.knobCenterYInHandle < startSemantics.stemTopYInHandle,
        )
        assertTrue(
            "Expected the end-handle knob below its stem, but got $endSemantics",
            endSemantics.knobCenterYInHandle > endSemantics.stemBottomYInHandle,
        )
    }

    @Test
    fun activeSelection_keepsHandleTouchTargetsAtLeastThirtyTwoDpWide() {
        composeRule.setReaderSelectionContent(
            chapterElements = listOf(
                ChapterElement.Text("Scholarship", id = "p1"),
            ),
        )

        composeRule.activateSelection()
        composeRule.waitForIdle()

        val expectedMinWidthPx = 32f * composeRule.activity.resources.displayMetrics.density
        val startBounds = composeRule
            .onNodeWithTag("reader_selection_handle_start", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val endBounds = composeRule
            .onNodeWithTag("reader_selection_handle_end", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected the start handle touch target to stay at least 32dp wide, but width was ${startBounds.width}px",
            startBounds.width >= expectedMinWidthPx - 1f,
        )
        assertTrue(
            "Expected the end handle touch target to stay at least 32dp wide, but width was ${endBounds.width}px",
            endBounds.width >= expectedMinWidthPx - 1f,
        )
    }

    @Test
    fun activeSelection_usesHalfHeightStemsForBothSelectionHandles() {
        val fontSizeSp = 12
        lateinit var selectionController: ReaderSelectionController
        lateinit var sectionId: String
        lateinit var density: Density
        val chapterElements = listOf(
            ChapterElement.Text("Scholarship", id = "p1"),
        )

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("selection_surface"),
                    ) {
                        val currentDensity = LocalDensity.current
                        val listState = rememberLazyListState()
                        val sections = remember(chapterElements) {
                            buildReaderChapterSections(chapterElements)
                        }
                        val selectionDocument = remember(sections) {
                            buildReaderSelectionDocument(sections)
                        }
                        SideEffect {
                            density = currentDensity
                        }
                        SideEffect {
                            sectionId = selectionDocument.sections.first().sectionId
                        }
                        ReaderChapterSelectionHost(
                            settings = GlobalSettings(
                                selectableText = true,
                                fontSize = fontSizeSp,
                            ),
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
                                        settings = GlobalSettings(
                                            selectableText = true,
                                            fontSize = fontSizeSp,
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
        val sectionNode = composeRule
            .onNodeWithTag("reader_compose_text_section", useUnmergedTree = true)
            .fetchSemanticsNode()
        val selectionOffset = Offset(
            x = sectionNode.size.width * 0.5f,
            y = sectionNode.size.height * 0.5f,
        )
        composeRule.runOnUiThread {
            selectionController.startSelectionAt(sectionId, selectionOffset)
            selectionController.updateSelectionGesture(sectionId, selectionOffset)
            selectionController.finishSelectionGesture()
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader_selection_handle_start", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("reader_selection_handle_end", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }

        val startSemantics = composeRule
            .onNodeWithTag("reader_selection_handle_start", useUnmergedTree = true)
            .fetchSemanticsNode().config[ReaderSelectionHandleSemanticsKey]
        val endSemantics = composeRule
            .onNodeWithTag("reader_selection_handle_end", useUnmergedTree = true)
            .fetchSemanticsNode().config[ReaderSelectionHandleSemanticsKey]

        val expectedStemHeightPx = with(density) { fontSizeSp.sp.toPx() * 0.5f }
        val startStemHeightPx = startSemantics.stemBottomYInHandle - startSemantics.stemTopYInHandle
        val endStemHeightPx = endSemantics.stemBottomYInHandle - endSemantics.stemTopYInHandle

        assertEquals(expectedStemHeightPx, startStemHeightPx, 1f)
        assertEquals(expectedStemHeightPx, endStemHeightPx, 1f)
        assertEquals(startStemHeightPx, endStemHeightPx, 1f)
    }

    @Test
    fun activeSelection_hidesSelectionHandlesWhenSelectedWordScrollsOffScreen() {
        composeRule.setReaderSelectionContent(
            chapterElements = listOf(
                ChapterElement.Text(
                    "Scholarship ".repeat(500).trim(),
                    id = "p1",
                ),
            ),
        )

        composeRule.activateSelection()

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
}
