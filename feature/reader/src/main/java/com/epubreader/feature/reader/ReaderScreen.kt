/*
 * AI_ENTRY_POINT
 * AI_READ_FIRST
 * AI_RELEVANT_TO: [Reader UI, Scroll Restoration, Reading Progress, Chapter Navigation]
 * AI_STATE_OWNER: Local composable state (currentChapterIndex, chapterElements)
 * AI_CRITICAL: Scroll restoration uses fragile LaunchedEffect ordering.
 *
 * ReaderScreen.kt
 *
 * MAIN READER COMPONENT: The state owner and effect coordinator for the reader.
 *
 * ARCHITECTURAL CONSTRAINTS:
 * 1. [AI_CRITICAL] Scroll Restoration: Relies on a complex state machine (isInitialScrollDone,
 *    isRestoringPosition) and precise timing (delay(100)). Modifying the order of operations in
 *    the chapter loading/restoration LaunchedEffects will cause "jumpy" UI or loss of reading position.
 * 2. [AI_NOTE] Global LTR: Enforces LayoutDirection.Ltr via CompositionLocalProvider to avoid
 *    mirroring issues with fixed-position UI elements (scrubbers, controls) in RTL locales.
 * 3. [AI_NOTE] Reader UI rendering now lives in helper files. Keep lifecycle/effect logic here and
 *    change `ReaderScreenChrome.kt` or `ReaderScreenControls.kt` only for presentational work.
 */
package com.epubreader.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * The main reader view. Handles chapter rendering, scroll position persistence,
 * TOC navigation, and reader settings (font, theme, etc.).
 *
 * [AI_CRITICAL] This composable manages three distinct "loading" states:
 * 1. book.id change: Resets the entire reader state.
 * 2. currentChapterIndex change: Loads new content from disk (IO).
 * 3. chapterElements change: Restores the scroll position within the loaded content.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ReaderScreen(
    book: EpubBook,
    settingsManager: SettingsManager,
    parser: EpubParser,
    onBack: () -> Unit,
) {
    // AI_STATE_OWNER: ReaderScreen composable
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val globalSettings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
    var settingsDraft by remember(book.id) { mutableStateOf<ReaderSettingsDraft?>(null) }
    val effectiveSettings = remember(globalSettings, settingsDraft) {
        settingsDraft?.applyTo(globalSettings) ?: globalSettings
    }

    // AI_STATE_OWNER: ReaderScreen (Owned here, reset on book change)
    var currentChapterIndex by remember(book.id) { mutableIntStateOf(-1) }
    // AI_STATE_OWNER: ReaderScreen (Extracted from EPUB, used for LazyColumn rendering)
    var chapterElements by remember(book.id) { mutableStateOf<List<ChapterElement>>(emptyList()) }
    var isLoadingChapter by remember { mutableStateOf(false) }
    var isInitialScrollDone by remember(book.id) { mutableStateOf(false) }
    var isRestoringPosition by remember { mutableStateOf(false) }
    var skipRestoration by remember { mutableStateOf(false) }
    var isGestureNavigation by remember { mutableStateOf(false) }
    var shouldScrollToBottom by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var selectionResetToken by remember(book.id) { mutableIntStateOf(0) }
    var isTextSelectionSessionActive by remember(book.id) { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val tocListState = rememberLazyListState()
    val themeColors = getThemeColors(globalSettings.theme, globalSettings.customThemes)

    var tocSort by remember { mutableStateOf(TocSort.Ascending) }
    val sortedToc = remember(book.toc, tocSort) {
        when (tocSort) {
            TocSort.Ascending -> book.toc
            TocSort.Descending -> book.toc.reversed()
        }
    }


    val progressPercentageState = rememberReaderProgressPercentageState(
        listState = listState,
        chapterElements = chapterElements,
    )

    // Overscroll Navigation State
    val verticalOverscrollState = remember { mutableFloatStateOf(0f) }
    var verticalOverscroll by verticalOverscrollState
    val density = LocalDensity.current
    val overscrollThreshold = with(density) { 80.dp.toPx() }

    // Performance-optimized auto-hide for overscroll notifications.
    // Uses debounce to prevent coroutine churn during fluid swipes.
    LaunchedEffect(Unit) {
        snapshotFlow { verticalOverscroll }
            .debounce(200.milliseconds)
            .collectLatest { value ->
                if (value != 0f) {
                    delay(1000)
                    verticalOverscroll = 0f
                }
            }
    }

    // 1. Initial State Sync: Runs once per book to find the last read chapter.
    LaunchedEffect(book.id) {
        if (currentChapterIndex == -1) {
            val savedProgress = settingsManager
                .getBookProgress(book.id, BookRepresentation.EPUB)
                .first()
            if (book.spineHrefs.isNotEmpty()) {
                val index = if (savedProgress.lastChapterHref != null) {
                    val foundIndex = book.spineHrefs.indexOf(savedProgress.lastChapterHref)
                    if (foundIndex != -1) foundIndex else 0
                } else {
                    0
                }
                currentChapterIndex = index.coerceIn(book.spineHrefs.indices)
            }
        }
    }

    // Load Chapter: Fetches content for currentChapterIndex and pre-fetches neighbors.
    // [AI_NOTE] Pre-fetching is done on Dispatchers.IO to prevent UI stutter during rapid navigation.
    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex == -1 || currentChapterIndex >= book.spineHrefs.size) return@LaunchedEffect

        isLoadingChapter = true
        val href = book.spineHrefs[currentChapterIndex]
        val elements = withContext(Dispatchers.IO) {
            parser.parseChapter(book.rootPath, href)
        }
        chapterElements = elements
        isLoadingChapter = false

        launch(Dispatchers.IO) {
            if (currentChapterIndex > 0) {
                parser.parseChapter(book.rootPath, book.spineHrefs[currentChapterIndex - 1])
            }
            if (currentChapterIndex < book.spineHrefs.size - 1) {
                parser.parseChapter(book.rootPath, book.spineHrefs[currentChapterIndex + 1])
            }
        }
    }

    LaunchedEffect(globalSettings, settingsDraft) {
        val draft = settingsDraft ?: return@LaunchedEffect
        if (draft.matches(globalSettings)) {
            settingsDraft = null
        }
    }

    /**
     * Restore Reading Position: THE MOST FRAGILE PART OF THE CODEBASE.
     * [AI_CRITICAL] Sequence is vital:
     * 1. Wait for LazyColumn to report totalItemsCount >= chapterElements.size using snapshotFlow.
     * 2. Perform the scroll (scrollToItem).
     * 3. delay(100) to allow the compose runtime to settle.
     * 4. Set isInitialScrollDone = true to enable the "Save Progress" effect.
     */
    LaunchedEffect(chapterElements) {
        if (chapterElements.isNotEmpty() && currentChapterIndex != -1 && currentChapterIndex < book.spineHrefs.size) {
            if (skipRestoration) {
                isRestoringPosition = true
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= chapterElements.size }
                    .first()
                delay(1)
                listState.scrollToItem(0, 0)
                delay(100)
                skipRestoration = false
                isInitialScrollDone = true
                isRestoringPosition = false
                isGestureNavigation = false
                shouldScrollToBottom = false
            } else if (isGestureNavigation) {
                isRestoringPosition = true
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= chapterElements.size }
                    .first()

                delay(1)
                if (shouldScrollToBottom) {
                    listState.scrollToItem(chapterElements.size - 1, 0)
                } else {
                    listState.scrollToItem(0, 0)
                }
                delay(100)
                isInitialScrollDone = true
                isRestoringPosition = false
                isGestureNavigation = false
                shouldScrollToBottom = false
            } else if (!isInitialScrollDone) {
                isRestoringPosition = true
                val savedProgress = settingsManager
                    .getBookProgress(book.id, BookRepresentation.EPUB)
                    .first()

                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= chapterElements.size }
                    .first()

                delay(1)
                if (skipRestoration) {
                    listState.scrollToItem(0, 0)
                    skipRestoration = false
                } else if (savedProgress.lastChapterHref == null || savedProgress.lastChapterHref == book.spineHrefs[currentChapterIndex]) {
                    if (savedProgress.lastChapterHref == book.spineHrefs[currentChapterIndex]) {
                        listState.scrollToItem(savedProgress.scrollIndex, savedProgress.scrollOffset)
                    } else {
                        listState.scrollToItem(0, 0)
                    }
                } else {
                    listState.scrollToItem(0, 0)
                }
                delay(100)
                isInitialScrollDone = true
                isRestoringPosition = false
            } else if (shouldScrollToBottom) {
                isRestoringPosition = true
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= chapterElements.size }
                    .first()
                delay(1)
                listState.scrollToItem(chapterElements.size - 1, 0)
                delay(100)
                shouldScrollToBottom = false
                isRestoringPosition = false
            }
        }
    }

    // Save Progress: Debounced to prevent coroutine thrashing during active scroll.
    // [AI_WARNING] Only saves if isInitialScrollDone is true AND we aren't currently restoring position.
    LaunchedEffect(book.id) {
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, currentChapterIndex)
        }
            .debounce(500.milliseconds)
            .collectLatest { (index, offset, chapterIndex) ->
                if (isInitialScrollDone && chapterElements.isNotEmpty() && !isRestoringPosition && chapterIndex != -1 && chapterIndex < book.spineHrefs.size) {
                    settingsManager.saveBookProgress(
                        book.id,
                        BookProgress(
                            scrollIndex = index,
                            scrollOffset = offset,
                            lastChapterHref = book.spineHrefs[chapterIndex]
                        ),
                        representation = BookRepresentation.EPUB,
                    )
                }
            }
    }

    suspend fun saveCurrentProgress() {
        saveReaderProgressSnapshot(
            book = book,
            settingsManager = settingsManager,
            currentChapterIndex = currentChapterIndex,
            listState = listState,
            isInitialScrollDone = isInitialScrollDone,
            isRestoringPosition = isRestoringPosition,
            isGestureNavigation = isGestureNavigation,
            skipRestoration = skipRestoration,
            shouldScrollToBottom = shouldScrollToBottom,
        )
    }

    suspend fun saveAndBack() {
        isTextSelectionSessionActive = false
        selectionResetToken++
        withFrameNanos { }
        saveCurrentProgress()
        onBack()
    }

    suspend fun scrollTocToCurrentChapter() {
        scrollReaderTocToCurrentChapter(
            currentChapterIndex = currentChapterIndex,
            book = book,
            sortedToc = sortedToc,
            tocListState = tocListState,
        )
    }

    fun locateCurrentChapterInToc() {
        scope.launch { scrollTocToCurrentChapter() }
    }

    fun jumpToChapter(targetIndex: Int) {
        showControls = false
        if (targetIndex != currentChapterIndex) {
            skipRestoration = true
            isInitialScrollDone = false
            isRestoringPosition = true
            shouldScrollToBottom = false
            currentChapterIndex = targetIndex
        } else {
            scope.launch { listState.scrollToItem(0, 0) }
        }
    }

    fun selectTocChapter(targetIndex: Int) {
        if (targetIndex != currentChapterIndex) {
            shouldScrollToBottom = false
            skipRestoration = true
            currentChapterIndex = targetIndex
            isInitialScrollDone = true
            isRestoringPosition = false
        } else {
            scope.launch { listState.animateScrollToItem(0) }
        }
        showControls = false
        scope.launch { drawerState.close() }
    }

    fun navigateNext() {
        if (currentChapterIndex < book.spineHrefs.size - 1) {
            shouldScrollToBottom = false
            isGestureNavigation = true
            isInitialScrollDone = true 
            isRestoringPosition = false
            currentChapterIndex++
        }
    }

    fun navigatePrev(toBottom: Boolean = true) {
        if (currentChapterIndex > 0) {
            shouldScrollToBottom = toBottom
            isGestureNavigation = true
            isInitialScrollDone = true 
            isRestoringPosition = false
            currentChapterIndex--
        }
    }

    fun releaseOverscroll() {
        if (verticalOverscroll >= overscrollThreshold) {
            navigatePrev()
        } else if (verticalOverscroll <= -overscrollThreshold) {
            navigateNext()
        }
        verticalOverscroll = 0f
    }

    fun handleMainScrubberDragStart() {
        showControls = false
        scope.launch { drawerState.close() }
        isInitialScrollDone = true
        isRestoringPosition = false
    }

    val nestedScrollConnection = rememberReaderNestedScrollConnection(
        listState = listState,
        isLoadingChapter = isLoadingChapter,
        isInitialScrollDone = isInitialScrollDone,
        overscrollThreshold = overscrollThreshold,
        verticalOverscroll = verticalOverscroll,
        onVerticalOverscrollChange = { verticalOverscroll = it },
    )

    BackHandler {
        when (
            resolveReaderBackAction(
                isDrawerOpen = drawerState.isOpen,
                isTextSelectionSessionActive = isTextSelectionSessionActive,
                showControls = showControls,
            )
        ) {
            ReaderBackAction.CloseToc -> scope.launch { drawerState.close() }
            ReaderBackAction.ClearTextSelection -> {
                isTextSelectionSessionActive = false
                selectionResetToken++
            }
            ReaderBackAction.HideControls -> showControls = false
            ReaderBackAction.ExitReader -> scope.launch { saveAndBack() }
        }
    }

    // Scroll TOC to current chapter
    LaunchedEffect(drawerState.currentValue, currentChapterIndex) {
        if (drawerState.currentValue == DrawerValue.Open) {
            scrollTocToCurrentChapter()
        }
    }

    ReaderSystemBarEffect(
        showControls = showControls,
        globalSettings = globalSettings,
    )

    val chromeState = buildReaderChromeState(
        book = book,
        settings = effectiveSettings,
        themeColors = themeColors,
        drawerState = drawerState,
        listState = listState,
        tocListState = tocListState,
        currentChapterIndex = currentChapterIndex,
        chapterElements = chapterElements,
        isLoadingChapter = isLoadingChapter,
        showControls = showControls,
        isTextSelectionSessionActive = isTextSelectionSessionActive,
        tocSort = tocSort,
        sortedToc = sortedToc,
        verticalOverscrollState = verticalOverscrollState,
        overscrollThreshold = overscrollThreshold,
        nestedScrollConnection = nestedScrollConnection,
        progressPercentageState = progressPercentageState,
        selectionResetToken = selectionResetToken,
    )
    val chromeCallbacks = buildReaderChromeCallbacks(
        onShowControlsChange = { showControls = it },
        onTextSelectionActiveChange = { isTextSelectionSessionActive = it },
        onClearTextSelection = {
            isTextSelectionSessionActive = false
            selectionResetToken++
        },
        onToggleTocSort = {
            tocSort = if (tocSort == TocSort.Ascending) TocSort.Descending else TocSort.Ascending
        },
        onReleaseOverscroll = ::releaseOverscroll,
        onSaveAndBack = { scope.launch { saveAndBack() } },
        onOpenToc = { scope.launch { drawerState.open() } },
        onCloseToc = { scope.launch { drawerState.close() } },
        onLocateCurrentChapterInToc = ::locateCurrentChapterInToc,
        onJumpToChapter = ::jumpToChapter,
        onSelectTocChapter = ::selectTocChapter,
        onPreviewSettings = { transform ->
            val previewedSettings = transform(effectiveSettings)
            settingsDraft = ReaderSettingsDraft.from(previewedSettings)
        },
        onPersistSettings = { transform ->
            val updatedSettings = transform(effectiveSettings)
            if (
                settingsDraft != null ||
                updatedSettings.fontSize != globalSettings.fontSize ||
                updatedSettings.lineHeight != globalSettings.lineHeight ||
                updatedSettings.horizontalPadding != globalSettings.horizontalPadding
            ) {
                settingsDraft = ReaderSettingsDraft.from(updatedSettings)
            }
            scope.launch {
                settingsManager.updateGlobalSettings(updatedSettings)
            }
        },
        onNavigatePrev = { navigatePrev(toBottom = false) },
        onNavigateNext = ::navigateNext,
        onMainScrubberDragStart = ::handleMainScrubberDragStart,
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ReaderScreenChrome(
            state = chromeState,
            callbacks = chromeCallbacks
        )
    }
}
