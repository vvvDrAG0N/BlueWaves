package com.epubreader.feature.reader.internal.shell
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.ui.GlobalSettingsTransform
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import com.epubreader.feature.reader.ReaderBackAction
import com.epubreader.feature.reader.ReaderSettingsDraft
import com.epubreader.feature.reader.TocSort
import com.epubreader.feature.reader.getThemeColors
import com.epubreader.feature.reader.internal.logReaderSelectionTransition
import com.epubreader.feature.reader.internal.runtime.epub.ReaderChapterSection
import com.epubreader.feature.reader.internal.runtime.epub.buildReaderChapterSections
import com.epubreader.feature.reader.internal.ui.ReaderScreenChrome
import com.epubreader.feature.reader.rememberReaderProgressPercentageState
import com.epubreader.feature.reader.resolveReaderBackAction
import com.epubreader.feature.reader.shouldForceClearReaderSelectionSession
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
internal fun ReaderFeatureShell(
    book: EpubBook,
    settingsManager: SettingsManager,
    parser: EpubParser,
    onBack: () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val globalSettings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
    var settingsDraft by remember(book.id) { mutableStateOf<ReaderSettingsDraft?>(null) }
    val effectiveSettings = remember(globalSettings, settingsDraft) {
        settingsDraft?.applyTo(globalSettings) ?: globalSettings
    }
    var currentChapterIndex by remember(book.id) { mutableIntStateOf(-1) }
    var chapterElements by remember(book.id) { mutableStateOf<List<ChapterElement>>(emptyList()) }
    var isLoadingChapter by remember { mutableStateOf(false) }
    var isChapterSettleComplete by remember(book.id) { mutableStateOf(false) }
    var isInitialScrollDone by remember(book.id) { mutableStateOf(false) }
    var isRestoringPosition by remember { mutableStateOf(false) }
    var hasReaderUserInteracted by remember(book.id) { mutableStateOf(false) }
    var skipRestoration by remember { mutableStateOf(false) }
    var isGestureNavigation by remember { mutableStateOf(false) }
    var shouldScrollToBottom by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var selectionSessionEpoch by remember(book.id) { mutableIntStateOf(0) }
    var isTextSelectionSessionActive by remember(book.id) { mutableStateOf(false) }
    var isSelectionHandleDragActive by remember(book.id) { mutableStateOf(false) }
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
    val chapterSections = remember(chapterElements) {
        buildReaderChapterSections(chapterElements)
    }
    val renderedItemCount = chapterSections.size
    val verticalOverscrollState = remember { mutableFloatStateOf(0f) }
    var verticalOverscroll by verticalOverscrollState
    val density = LocalDensity.current
    val overscrollThreshold = with(density) { 80.dp.toPx() }
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
    ReaderChapterLoadingEffect(
        book = book,
        parser = parser,
        currentChapterIndex = currentChapterIndex,
        hasChapterElements = chapterElements.isNotEmpty(),
        isLoadingChapter = isLoadingChapter,
        isChapterSettleComplete = isChapterSettleComplete,
        hasReaderUserInteracted = hasReaderUserInteracted,
        onLoadingChapterChange = { isLoadingChapter = it },
        onChapterElementsChange = { chapterElements = it },
        onChapterSettleCompleteChange = { isChapterSettleComplete = it },
    )
    fun invalidateSelectionSession(reason: String) {
        logReaderSelectionTransition {
            "selection.shell.invalidate reason=$reason chapter=$currentChapterIndex epoch=$selectionSessionEpoch active=$isTextSelectionSessionActive handleDrag=$isSelectionHandleDragActive"
        }
        isTextSelectionSessionActive = false
        isSelectionHandleDragActive = false
        selectionSessionEpoch++
    }
    LaunchedEffect(globalSettings, settingsDraft) {
        val draft = settingsDraft ?: return@LaunchedEffect
        if (draft.matches(globalSettings)) {
            settingsDraft = null
        }
    }
    LaunchedEffect(effectiveSettings.selectableText, isTextSelectionSessionActive) {
        if (
            shouldForceClearReaderSelectionSession(
                selectableTextEnabled = effectiveSettings.selectableText,
                isTextSelectionSessionActive = isTextSelectionSessionActive,
            )
        ) {
            invalidateSelectionSession(reason = "selectableTextDisabled")
        }
    }
    LaunchedEffect(chapterElements) {
        if (chapterElements.isNotEmpty() && currentChapterIndex != -1 && currentChapterIndex < book.spineHrefs.size) {
            if (skipRestoration) {
                isRestoringPosition = true
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= renderedItemCount }
                    .first()
                delay(1)
                listState.scrollToItem(0, 0)
                delay(100)
                skipRestoration = false
                isInitialScrollDone = true
                isRestoringPosition = false
                isChapterSettleComplete = true
                isGestureNavigation = false
                shouldScrollToBottom = false
            } else if (isGestureNavigation) {
                isRestoringPosition = true
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= renderedItemCount }
                    .first()
                delay(1)
                if (shouldScrollToBottom) {
                    listState.scrollToItem(renderedItemCount - 1, 0)
                } else {
                    listState.scrollToItem(0, 0)
                }
                delay(100)
                isInitialScrollDone = true
                isRestoringPosition = false
                isChapterSettleComplete = true
                isGestureNavigation = false
                shouldScrollToBottom = false
            } else if (!isInitialScrollDone) {
                isRestoringPosition = true
                val savedProgress = settingsManager
                    .getBookProgress(book.id, BookRepresentation.EPUB)
                    .first()
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= renderedItemCount }
                    .first()
                delay(1)
                if (skipRestoration) {
                    listState.scrollToItem(0, 0)
                    skipRestoration = false
                } else if (savedProgress.lastChapterHref == null || savedProgress.lastChapterHref == book.spineHrefs[currentChapterIndex]) {
                    if (savedProgress.lastChapterHref == book.spineHrefs[currentChapterIndex]) {
                        listState.scrollToItem(
                            mapReaderProgressIndexToRenderedItem(
                                progressIndex = savedProgress.scrollIndex,
                                chapterSections = chapterSections,
                            ),
                            savedProgress.scrollOffset,
                        )
                    } else {
                        listState.scrollToItem(0, 0)
                    }
                } else {
                    listState.scrollToItem(0, 0)
                }
                delay(100)
                isInitialScrollDone = true
                isRestoringPosition = false
                isChapterSettleComplete = true
            } else if (shouldScrollToBottom) {
                isRestoringPosition = true
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= renderedItemCount }
                    .first()
                delay(1)
                listState.scrollToItem(renderedItemCount - 1, 0)
                delay(100)
                shouldScrollToBottom = false
                isRestoringPosition = false
                isChapterSettleComplete = true
            }
        }
    }
    LaunchedEffect(book.id) {
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, currentChapterIndex)
        }
            .debounce(500.milliseconds)
            .collectLatest { (index, offset, chapterIndex) ->
                if (
                    isInitialScrollDone &&
                    chapterElements.isNotEmpty() &&
                    !isRestoringPosition &&
                    !isSelectionHandleDragActive &&
                    chapterIndex != -1 &&
                    chapterIndex < book.spineHrefs.size
                ) {
                    settingsManager.saveBookProgress(
                        book.id,
                        BookProgress(
                            scrollIndex = mapRenderedItemIndexToReaderProgressIndex(
                                renderedItemIndex = index,
                                chapterSections = chapterSections,
                            ),
                            scrollOffset = offset,
                            lastChapterHref = book.spineHrefs[chapterIndex],
                        ),
                        representation = BookRepresentation.EPUB,
                    )
                }
            }
    }
    LaunchedEffect(book.id) {
        snapshotFlow { listState.isScrollInProgress && isInitialScrollDone && !isRestoringPosition }
            .collectLatest { hasUserDrivenScroll ->
                if (hasUserDrivenScroll) {
                    hasReaderUserInteracted = true
                }
            }
    }
    suspend fun saveCurrentProgress() {
        saveReaderProgressSnapshot(
            book = book,
            settingsManager = settingsManager,
            currentChapterIndex = currentChapterIndex,
            listState = listState,
            chapterSections = chapterSections,
            isInitialScrollDone = isInitialScrollDone,
            isRestoringPosition = isRestoringPosition,
            isGestureNavigation = isGestureNavigation,
            skipRestoration = skipRestoration,
            shouldScrollToBottom = shouldScrollToBottom,
        )
    }
    suspend fun saveAndBack() {
        invalidateSelectionSession(reason = "saveAndBack")
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
        invalidateSelectionSession(reason = "jumpToChapter")
        showControls = false
        hasReaderUserInteracted = true
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
        invalidateSelectionSession(reason = "selectTocChapter")
        hasReaderUserInteracted = true
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
            invalidateSelectionSession(reason = "navigateNext")
            hasReaderUserInteracted = true
            shouldScrollToBottom = false
            isGestureNavigation = true
            isInitialScrollDone = true
            isRestoringPosition = false
            currentChapterIndex++
        }
    }
    fun navigatePrev(toBottom: Boolean = true) {
        if (currentChapterIndex > 0) {
            invalidateSelectionSession(reason = "navigatePrev")
            hasReaderUserInteracted = true
            shouldScrollToBottom = toBottom
            isGestureNavigation = true
            isInitialScrollDone = true
            isRestoringPosition = false
            currentChapterIndex--
        }
    }
    fun releaseOverscroll() {
        logReaderSelectionTransition { "selection.shell.releaseOverscroll value=$verticalOverscroll threshold=$overscrollThreshold chapter=$currentChapterIndex epoch=$selectionSessionEpoch" }
        if (verticalOverscroll >= overscrollThreshold) {
            navigatePrev()
        } else if (verticalOverscroll <= -overscrollThreshold) {
            navigateNext()
        }
        verticalOverscroll = 0f
    }
    fun handleMainScrubberDragStart() {
        invalidateSelectionSession(reason = "mainScrubberDragStart")
        showControls = false
        hasReaderUserInteracted = true
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
            ReaderBackAction.ClearTextSelection -> invalidateSelectionSession(reason = "backClearSelection")
            ReaderBackAction.HideControls -> showControls = false
            ReaderBackAction.ExitReader -> scope.launch { saveAndBack() }
        }
    }
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
        renderedItemCount = renderedItemCount,
        isLoadingChapter = isLoadingChapter,
        showControls = showControls,
        isTextSelectionSessionActive = isTextSelectionSessionActive,
        tocSort = tocSort,
        sortedToc = sortedToc,
        verticalOverscrollState = verticalOverscrollState,
        overscrollThreshold = overscrollThreshold,
        nestedScrollConnection = nestedScrollConnection,
        progressPercentageState = progressPercentageState,
        selectionSessionEpoch = selectionSessionEpoch,
    )
    val chromeCallbacks = buildReaderChromeCallbacks(
        onShowControlsChange = { shouldShow ->
            if (shouldShow) {
                invalidateSelectionSession(reason = "showControls")
            }
            showControls = shouldShow
        },
        onTextSelectionActiveChange = { epoch, isActive ->
            if (epoch == selectionSessionEpoch) {
                isTextSelectionSessionActive = isActive
            } else {
                logReaderSelectionTransition {
                    "selection.shell.ignoreActiveCallback staleEpoch=$epoch currentEpoch=$selectionSessionEpoch active=$isActive"
                }
            }
        },
        onSelectionHandleDragChange = { epoch, isDragging ->
            if (epoch == selectionSessionEpoch) {
                isSelectionHandleDragActive = isDragging
            } else {
                logReaderSelectionTransition {
                    "selection.shell.ignoreHandleCallback staleEpoch=$epoch currentEpoch=$selectionSessionEpoch dragging=$isDragging"
                }
            }
        },
        onClearTextSelection = { invalidateSelectionSession(reason = "chromeClearSelection") },
        onToggleTocSort = {
            tocSort = if (tocSort == TocSort.Ascending) TocSort.Descending else TocSort.Ascending
        },
        onReleaseOverscroll = ::releaseOverscroll,
        onSaveAndBack = { scope.launch { saveAndBack() } },
        onOpenToc = {
            invalidateSelectionSession(reason = "openToc")
            scope.launch { drawerState.open() }
        },
        onCloseToc = { scope.launch { drawerState.close() } },
        onLocateCurrentChapterInToc = ::locateCurrentChapterInToc,
        onJumpToChapter = ::jumpToChapter,
        onSelectTocChapter = ::selectTocChapter,
        onPreviewSettings = { transform ->
            val previewedSettings = transform(effectiveSettings)
            settingsDraft = ReaderSettingsDraft.from(previewedSettings)
        },
        onPersistSettings = { transform: GlobalSettingsTransform ->
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
            callbacks = chromeCallbacks,
        )
    }
}
