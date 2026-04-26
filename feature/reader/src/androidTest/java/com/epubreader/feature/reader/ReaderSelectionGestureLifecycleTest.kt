package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionHandleSemanticsKey
import com.epubreader.feature.reader.internal.runtime.epub.readerSelectionLongPressGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSelectionGestureLifecycleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun plainLongClick_keepsBothSelectionHandlesVisible() {
        setReaderContent()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true).performTouchInput {
            longClick(
                Offset(
                    x = width * 0.35f,
                    y = height * 0.35f,
                ),
            )
        }

        waitForSelectionHandles()
        assertHandleVisibleBySemantics("reader_selection_handle_start")
        assertHandleVisibleBySemantics("reader_selection_handle_end")
    }

    @Test
    fun slightFingerDriftAfterLongPress_keepsEndHandleVisible() {
        setReaderContent()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true).performTouchInput {
            down(
                Offset(
                    x = width * 0.35f,
                    y = height * 0.35f,
                ),
            )
            advanceEventTime(700)
            moveBy(Offset(4f, 3f))
            advanceEventTime(120)
            moveBy(Offset(2f, 1f))
            advanceEventTime(120)
            up()
        }

        waitForSelectionHandles()
        assertHandleVisibleBySemantics("reader_selection_handle_end")
    }

    @Test
    fun gestureCancellation_midDragStillInvokesLongPressEnd() {
        var dragCount = 0
        var endCount = 0

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    var gestureEnabled by remember { mutableStateOf(true) }
                    var latestDragCount by remember { mutableIntStateOf(0) }
                    var latestEndCount by remember { mutableIntStateOf(0) }

                    dragCount = latestDragCount
                    endCount = latestEndCount

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("gesture_surface")
                            .then(
                                if (gestureEnabled) {
                                    Modifier.readerSelectionLongPressGesture(
                                        onLongPressStart = {},
                                        onLongPressDrag = {
                                            latestDragCount += 1
                                            dragCount = latestDragCount
                                            gestureEnabled = false
                                        },
                                        onLongPressEnd = {
                                            latestEndCount += 1
                                            endCount = latestEndCount
                                        },
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gesture_surface", useUnmergedTree = true).performTouchInput {
            down(Offset(centerX, centerY))
            advanceEventTime(700)
            moveBy(Offset(6f, 4f))
            advanceEventTime(120)
            up()
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertTrue("Expected the long-press drag to run before cancellation", dragCount > 0)
            assertEquals("Expected cancellation to still invoke onLongPressEnd", 1, endCount)
        }
    }

    private fun setReaderContent() {
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
                            chapterElements = listOf(
                                ChapterElement.Text(
                                    "Scholarship keeps the selection lifecycle steady while the handle survives tiny finger drift. ".repeat(24).trim(),
                                    id = "p1",
                                ),
                            ),
                            isLoadingChapter = false,
                            currentChapterIndex = 0,
                        )
                    }
                }
            }
        }
    }

    private fun waitForSelectionHandles() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("text_selection_action_bar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader_selection_handle_start", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("reader_selection_handle_end", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertHandleVisibleBySemantics(tag: String) {
        val semantics = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[ReaderSelectionHandleSemanticsKey]
        assertTrue("Expected $tag to exist", semantics.touchTargetHeightPx > 0f)
        assertFalse("Expected $tag to stay visible, but was $semantics", semantics.isHidden)
    }
}
