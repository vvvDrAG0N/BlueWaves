package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderChromeTapBehaviorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun selectableTextEnabled_tappingChapterSurfaceWithSlightFingerDriftStillTogglesReaderControlsWhenNoSelectionIsActive() {
        var controlsToggled = false

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val listState = rememberLazyListState()
                val tocListState = rememberLazyListState()
                val progressState = remember { mutableFloatStateOf(0f) }
                val overscrollState = remember { mutableFloatStateOf(0f) }
                var showControls by remember { mutableStateOf(false) }

                val state = ReaderChromeState(
                    book = EpubBook(
                        id = "book-1",
                        title = "Sample",
                        author = "Author",
                        coverPath = null,
                        rootPath = composeRule.activity.filesDir.absolutePath,
                        spineHrefs = listOf("chapter-1.xhtml"),
                    ),
                    settings = GlobalSettings(selectableText = true),
                    themeColors = getThemeColors("light"),
                    drawerState = drawerState,
                    listState = listState,
                    tocListState = tocListState,
                    currentChapterIndex = 0,
                    chapterElements = listOf(ChapterElement.Text("Paragraph one", id = "p1")),
                    renderedItemCount = 1,
                    isLoadingChapter = false,
                    showControls = showControls,
                    isTextSelectionSessionActive = false,
                    tocSort = TocSort.Ascending,
                    sortedToc = emptyList(),
                    verticalOverscrollState = overscrollState,
                    overscrollThreshold = 80f,
                    nestedScrollConnection = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {} },
                    progressPercentageState = progressState,
                    selectionSessionEpoch = 0,
                )
                val callbacks = ReaderChromeCallbacks(
                    onShowControlsChange = {
                        showControls = it
                        controlsToggled = true
                    },
                    onTextSelectionActiveChange = { _, _ -> },
                    onClearTextSelection = {},
                    onToggleTocSort = {},
                    onReleaseOverscroll = {},
                    onSaveAndBack = {},
                    onOpenToc = {},
                    onCloseToc = {},
                    onLocateCurrentChapterInToc = {},
                    onJumpToChapter = {},
                    onSelectTocChapter = {},
                    onPreviewSettings = {},
                    onPersistSettings = {},
                    onNavigatePrev = {},
                    onNavigateNext = {},
                    onMainScrubberDragStart = {},
                )

                MaterialTheme {
                    ReaderScreenChrome(
                        state = state,
                        callbacks = callbacks,
                    )
                }
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("reader_controls_overlay", useUnmergedTree = true).performTouchInput {
            down(Offset(centerX, centerY))
            advanceEventTime(40)
            moveBy(Offset(6f, 4f))
            up()
        }

        composeRule.waitUntil(5_000) { controlsToggled }
        assertTrue(controlsToggled)
    }

    @Test
    fun selectableTextEnabled_longPressingReaderTextStartsSelectionWithoutTogglingReaderControls() {
        var controlsToggled = false
        var selectionActive = false

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val listState = rememberLazyListState()
                val tocListState = rememberLazyListState()
                val progressState = remember { mutableFloatStateOf(0f) }
                val overscrollState = remember { mutableFloatStateOf(0f) }
                var showControls by remember { mutableStateOf(false) }
                var selectionSessionEpoch by remember { mutableIntStateOf(0) }
                var isTextSelectionSessionActive by remember { mutableStateOf(false) }

                val state = ReaderChromeState(
                    book = EpubBook(
                        id = "book-1",
                        title = "Sample",
                        author = "Author",
                        coverPath = null,
                        rootPath = composeRule.activity.filesDir.absolutePath,
                        spineHrefs = listOf("chapter-1.xhtml"),
                    ),
                    settings = GlobalSettings(selectableText = true),
                    themeColors = getThemeColors("light"),
                    drawerState = drawerState,
                    listState = listState,
                    tocListState = tocListState,
                    currentChapterIndex = 0,
                    chapterElements = listOf(
                        ChapterElement.Text(
                            "Paragraph one ".repeat(40).trim(),
                            id = "p1",
                        ),
                    ),
                    renderedItemCount = 1,
                    isLoadingChapter = false,
                    showControls = showControls,
                    isTextSelectionSessionActive = isTextSelectionSessionActive,
                    tocSort = TocSort.Ascending,
                    sortedToc = emptyList(),
                    verticalOverscrollState = overscrollState,
                    overscrollThreshold = 80f,
                    nestedScrollConnection = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {} },
                    progressPercentageState = progressState,
                    selectionSessionEpoch = selectionSessionEpoch,
                )
                val callbacks = ReaderChromeCallbacks(
                    onShowControlsChange = {
                        showControls = it
                        controlsToggled = true
                    },
                    onTextSelectionActiveChange = { epoch, isActive ->
                        if (epoch == selectionSessionEpoch) {
                            isTextSelectionSessionActive = isActive
                            selectionActive = isActive
                        }
                    },
                    onClearTextSelection = {
                        isTextSelectionSessionActive = false
                        selectionActive = false
                        selectionSessionEpoch++
                    },
                    onSelectionHandleDragChange = { _, _ -> },
                    onToggleTocSort = {},
                    onReleaseOverscroll = {},
                    onSaveAndBack = {},
                    onOpenToc = {},
                    onCloseToc = {},
                    onLocateCurrentChapterInToc = {},
                    onJumpToChapter = {},
                    onSelectTocChapter = {},
                    onPreviewSettings = {},
                    onPersistSettings = {},
                    onNavigatePrev = {},
                    onNavigateNext = {},
                    onMainScrubberDragStart = {},
                )

                MaterialTheme {
                    ReaderScreenChrome(
                        state = state,
                        callbacks = callbacks,
                    )
                }
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true).performTouchInput {
            longClick(Offset(120f, 120f))
        }

        composeRule.waitUntil(5_000) { selectionActive }
        composeRule.onNodeWithTag("text_selection_action_bar", useUnmergedTree = true).assertExists()
        assertTrue(selectionActive)
        assertFalse(controlsToggled)
    }
}
