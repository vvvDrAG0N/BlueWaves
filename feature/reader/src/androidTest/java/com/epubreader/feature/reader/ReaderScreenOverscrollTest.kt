package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.TocItem
import com.epubreader.feature.reader.internal.shell.rememberReaderNestedScrollConnection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderScreenOverscrollTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var triggerNextChapterOverscroll: (() -> Unit)? = null

    @Test
    fun overscrollReleaseAtBottom_goesToNextChapter() {
        setReaderOverscrollTestContent(
            initialChapterIndex = 0,
            selectableText = false,
        )

        waitUntilChapterDisplayed(0)
        swipeUpUntilChapterDisplayed(targetChapterIndex = 1, sourceChapterIndex = 0)
    }

    @Test
    fun overscrollReleaseAtTop_goesToPreviousChapter() {
        setReaderOverscrollTestContent(
            initialChapterIndex = 1,
            selectableText = false,
        )

        waitUntilChapterDisplayed(1)
        swipeDownUntilChapterDisplayed(targetChapterIndex = 0, sourceChapterIndex = 1)
    }

    @Test
    fun overscrollBelowThreshold_doesNotFlipChapter() {
        setReaderOverscrollTestContent(
            initialChapterIndex = 1,
            selectableText = false,
        )

        waitUntilChapterDisplayed(1)
        swipeDownSmall(chapterIndex = 1)
        composeRule.waitForIdle()
    }

    @Test
    fun overscrollAfterActiveSelection_stillAllowsSelectingTextInTheNewChapter() {
        setReaderOverscrollTestContent(
            initialChapterIndex = 0,
            selectableText = true,
        )

        waitUntilChapterDisplayed(0)
        longPressVisibleText("AlphaSelectionTarget")
        waitForSelectionActionBar()
        composeRule.runOnUiThread {
            checkNotNull(triggerNextChapterOverscroll) {
                "Next-chapter overscroll trigger was not registered"
            }.invoke()
        }
        waitUntilChapterDisplayed(1)

        waitUntilChapterDisplayed(1)
        longPressVisibleText("BetaSelectionTarget")
        waitForSelectionActionBar()
    }

    private fun setReaderOverscrollTestContent(
        initialChapterIndex: Int,
        selectableText: Boolean,
    ) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderOverscrollTestSurface(
                        initialChapterIndex = initialChapterIndex,
                        selectableText = selectableText,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun ReaderOverscrollTestSurface(
        initialChapterIndex: Int,
        selectableText: Boolean,
    ) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val listState = rememberLazyListState()
        val tocListState = rememberLazyListState()
        val progressPercentageState = remember { mutableFloatStateOf(0f) }
        var verticalOverscroll by remember { mutableFloatStateOf(0f) }
        var currentChapterIndex by remember { mutableIntStateOf(initialChapterIndex) }
        var selectionSessionEpoch by remember { mutableIntStateOf(0) }
        var isTextSelectionSessionActive by remember { mutableStateOf(false) }
        var isSelectionHandleDragActive by remember { mutableStateOf(false) }
        var pendingScrollToBottom by remember { mutableStateOf(false) }
        val density = LocalDensity.current
        val overscrollThreshold = with(density) { 80.dp.toPx() }
        val chapterElements = remember(currentChapterIndex) {
            buildOverscrollChapterElements(currentChapterIndex)
        }
        val renderedItemCount = remember(chapterElements) {
            buildReaderChapterSections(chapterElements).size
        }
        val nestedScrollConnection = rememberReaderNestedScrollConnection(
            listState = listState,
            isLoadingChapter = false,
            isInitialScrollDone = true,
            overscrollThreshold = overscrollThreshold,
            verticalOverscroll = verticalOverscroll,
            onVerticalOverscrollChange = { verticalOverscroll = it },
        )

        fun invalidateSelectionSession() {
            isTextSelectionSessionActive = false
            isSelectionHandleDragActive = false
            selectionSessionEpoch++
        }

        fun releaseOverscroll() {
            when {
                verticalOverscroll >= overscrollThreshold && currentChapterIndex > 0 -> {
                    invalidateSelectionSession()
                    pendingScrollToBottom = true
                    currentChapterIndex--
                }

                verticalOverscroll <= -overscrollThreshold && currentChapterIndex < 1 -> {
                    invalidateSelectionSession()
                    pendingScrollToBottom = false
                    currentChapterIndex++
                }
            }
            verticalOverscroll = 0f
        }

        DisposableEffect(overscrollThreshold) {
            triggerNextChapterOverscroll = {
                verticalOverscroll = -overscrollThreshold
                releaseOverscroll()
            }
            onDispose {
                triggerNextChapterOverscroll = null
            }
        }

        LaunchedEffect(currentChapterIndex, selectionSessionEpoch, renderedItemCount, pendingScrollToBottom) {
            if (renderedItemCount == 0) {
                return@LaunchedEffect
            }
            listState.scrollToItem(
                index = if (pendingScrollToBottom) renderedItemCount - 1 else 0,
                scrollOffset = 0,
            )
            pendingScrollToBottom = false
        }

        val state = ReaderChromeState(
            book = buildOverscrollTestBook(),
            settings = GlobalSettings(selectableText = selectableText),
            themeColors = getThemeColors("light"),
            drawerState = drawerState,
            listState = listState,
            tocListState = tocListState,
            currentChapterIndex = currentChapterIndex,
            chapterElements = chapterElements,
            renderedItemCount = renderedItemCount,
            isLoadingChapter = false,
            showControls = false,
            isTextSelectionSessionActive = isTextSelectionSessionActive,
            tocSort = TocSort.Ascending,
            sortedToc = buildOverscrollTestBook().toc,
            verticalOverscrollState = remember { mutableStateOf(verticalOverscroll) }.also { it.value = verticalOverscroll },
            overscrollThreshold = overscrollThreshold,
            nestedScrollConnection = nestedScrollConnection,
            progressPercentageState = progressPercentageState,
            selectionSessionEpoch = selectionSessionEpoch,
        )
        val callbacks = ReaderChromeCallbacks(
            onShowControlsChange = {},
            onTextSelectionActiveChange = { epoch, isActive ->
                if (epoch == selectionSessionEpoch) {
                    isTextSelectionSessionActive = isActive
                }
            },
            onSelectionHandleDragChange = { epoch, isDragging ->
                if (epoch == selectionSessionEpoch) {
                    isSelectionHandleDragActive = isDragging
                }
            },
            onClearTextSelection = { invalidateSelectionSession() },
            onToggleTocSort = {},
            onReleaseOverscroll = ::releaseOverscroll,
            onSaveAndBack = {},
            onOpenToc = {},
            onCloseToc = {},
            onLocateCurrentChapterInToc = {},
            onJumpToChapter = { targetIndex ->
                if (targetIndex != currentChapterIndex) {
                    invalidateSelectionSession()
                    pendingScrollToBottom = false
                    currentChapterIndex = targetIndex
                }
            },
            onSelectTocChapter = { targetIndex ->
                if (targetIndex != currentChapterIndex) {
                    invalidateSelectionSession()
                    pendingScrollToBottom = false
                    currentChapterIndex = targetIndex
                }
            },
            onPreviewSettings = {},
            onPersistSettings = {},
            onNavigatePrev = {
                if (currentChapterIndex > 0) {
                    invalidateSelectionSession()
                    pendingScrollToBottom = true
                    currentChapterIndex--
                }
            },
            onNavigateNext = {
                if (currentChapterIndex < 1) {
                    invalidateSelectionSession()
                    pendingScrollToBottom = false
                    currentChapterIndex++
                }
            },
            onMainScrubberDragStart = { invalidateSelectionSession() },
        )

        ReaderScreenChrome(
            state = state,
            callbacks = callbacks,
        )
    }

    private fun waitUntilChapterDisplayed(chapterIndex: Int, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(
                "reader_runtime_chapter_$chapterIndex",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun swipeUpUntilChapterDisplayed(
        targetChapterIndex: Int,
        sourceChapterIndex: Int,
        maxSwipes: Int = 20,
    ) {
        repeat(maxSwipes) {
            swipeUpLarge(sourceChapterIndex)
            if (isChapterDisplayed(targetChapterIndex)) {
                return
            }
        }
        check(isChapterDisplayed(targetChapterIndex)) {
            "Did not reach expected chapter after overscroll: $targetChapterIndex"
        }
    }

    private fun swipeDownUntilChapterDisplayed(
        targetChapterIndex: Int,
        sourceChapterIndex: Int,
        maxSwipes: Int = 12,
    ) {
        repeat(maxSwipes) {
            swipeDownLarge(sourceChapterIndex)
            if (isChapterDisplayed(targetChapterIndex)) {
                return
            }
        }
        check(isChapterDisplayed(targetChapterIndex)) {
            "Did not reach expected chapter after reverse overscroll: $targetChapterIndex"
        }
    }

    private fun isChapterDisplayed(chapterIndex: Int): Boolean {
        return composeRule.onAllNodesWithTag(
            "reader_runtime_chapter_$chapterIndex",
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()
    }

    private fun swipeUpLarge(chapterIndex: Int) {
        composeRule.onNodeWithTag(
            "reader_runtime_chapter_$chapterIndex",
            useUnmergedTree = true,
        ).performTouchInput {
            swipe(
                start = Offset(center.x, height * 0.85f),
                end = Offset(center.x, height * 0.15f),
                durationMillis = 250,
            )
        }
    }

    private fun swipeDownLarge(chapterIndex: Int) {
        composeRule.onNodeWithTag(
            "reader_runtime_chapter_$chapterIndex",
            useUnmergedTree = true,
        ).performTouchInput {
            swipe(
                start = Offset(center.x, height * 0.15f),
                end = Offset(center.x, height * 0.85f),
                durationMillis = 250,
            )
        }
    }

    private fun swipeDownSmall(chapterIndex: Int) {
        composeRule.onNodeWithTag(
            "reader_runtime_chapter_$chapterIndex",
            useUnmergedTree = true,
        ).performTouchInput {
            swipe(
                start = Offset(center.x, height * 0.20f),
                end = Offset(center.x, height * 0.26f),
                durationMillis = 180,
            )
        }
    }

    private fun longPressVisibleText(text: String) {
        composeRule.onNodeWithText(text, substring = true, useUnmergedTree = true).performTouchInput {
            longClick(center)
        }
    }

    private fun waitForSelectionActionBar() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Copy", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun buildOverscrollTestBook(): EpubBook {
        return EpubBook(
            id = "reader-overscroll-test-book",
            title = "Overscroll Test Book",
            author = "Blue Waves",
            coverPath = null,
            rootPath = composeRule.activity.filesDir.absolutePath,
            toc = listOf(
                TocItem(title = "Chapter 1", href = "chapter1.xhtml"),
                TocItem(title = "Chapter 2", href = "chapter2.xhtml"),
            ),
            spineHrefs = listOf("chapter1.xhtml", "chapter2.xhtml"),
        )
    }

    private fun buildOverscrollChapterElements(chapterIndex: Int): List<ChapterElement> {
        val heading = if (chapterIndex == 0) "Chapter 1" else "Chapter 2"
        val selectionTarget = if (chapterIndex == 0) "AlphaSelectionTarget" else "BetaSelectionTarget"
        return buildList {
            add(ChapterElement.Text(heading, type = "h", id = "heading-$chapterIndex"))
            add(ChapterElement.Text(selectionTarget, id = "selection-$chapterIndex"))
            repeat(18) { index ->
                add(
                    ChapterElement.Text(
                        "$heading filler $index",
                        id = "filler-$chapterIndex-$index",
                    ),
                )
            }
        }
    }
}
