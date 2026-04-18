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

import android.app.Activity
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
@OptIn(ExperimentalMaterial3Api::class)
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

    // Overscroll Navigation State
    var verticalOverscroll by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val overscrollThreshold = with(density) { 80.dp.toPx() }

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

        scope.launch(Dispatchers.IO) {
            if (currentChapterIndex > 0) {
                parser.parseChapter(book.rootPath, book.spineHrefs[currentChapterIndex - 1])
            }
            if (currentChapterIndex < book.spineHrefs.size - 1) {
                parser.parseChapter(book.rootPath, book.spineHrefs[currentChapterIndex + 1])
            }
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
        if (currentChapterIndex == -1 || currentChapterIndex >= book.spineHrefs.size) {
            return
        }

        withContext(NonCancellable) {
            val progress = when {
                isInitialScrollDone && !isRestoringPosition -> {
                    BookProgress(
                        scrollIndex = listState.firstVisibleItemIndex,
                        scrollOffset = listState.firstVisibleItemScrollOffset,
                        lastChapterHref = book.spineHrefs[currentChapterIndex]
                    )
                }

                isGestureNavigation || skipRestoration -> {
                    BookProgress(
                        scrollIndex = if (shouldScrollToBottom) Int.MAX_VALUE else 0,
                        scrollOffset = 0,
                        lastChapterHref = book.spineHrefs[currentChapterIndex]
                    )
                }

                else -> null
            }
            progress?.let {
                settingsManager.saveBookProgress(
                    book.id,
                    it,
                    representation = BookRepresentation.EPUB,
                )
            }
        }
    }

    suspend fun saveAndBack() {
        saveCurrentProgress()
        onBack()
    }

    suspend fun scrollTocToCurrentChapter() {
        if (currentChapterIndex != -1 && currentChapterIndex < book.spineHrefs.size) {
            val currentHref = book.spineHrefs[currentChapterIndex]
            val tocIndex = sortedToc.indexOfFirst { it.href.substringBefore("#") == currentHref }
            if (tocIndex != -1) {
                if (tocListState.layoutInfo.totalItemsCount == 0) {
                    snapshotFlow { tocListState.layoutInfo.totalItemsCount }
                        .filter { it > 0 }
                        .first()
                }
                delay(1)
                tocListState.animateScrollToItem(index = tocIndex, scrollOffset = -250)
            }
        }
    }

    fun locateCurrentChapterInToc() {
        scope.launch { scrollTocToCurrentChapter() }
    }

    /**
     * Go to Chapter Flow (Numeric Jump):
     * Triggered by direct numeric input in the TOC drawer.
     *
     * Unlike [selectTocChapter], this uses [jumpToChapter] logic which:
     * 1. Resets `isInitialScrollDone = false` to trigger a fresh restoration cycle.
     * 2. Sets `skipRestoration = true` to force the scroll to the top of the target chapter.
     * 3. Sets `isRestoringPosition = true` to block progress saving until the jump completes.
     */
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

    /**
     * TOC Selection Flow:
     * Called when a specific chapter is selected from the Table of Contents drawer.
     *
     * [AI_CRITICAL] differs from [navigateNext] in how it handles restoration:
     * 1. Forces `isInitialScrollDone = true` to bypass progress-based restoration for the new chapter.
     * 2. Sets `skipRestoration = true` to ensure the next chapter load snaps to the top (index 0).
     * 3. Resets `isRestoringPosition = false` to allow immediate UI interaction.
     */
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

    /**
     * Gesture Navigation Flow:
     * Triggered by overscroll (Pull-to-Next/Prev) or bottom bar navigation buttons.
     *
     * [AI_CRITICAL] Restoration Logic:
     * 1. Sets `isGestureNavigation = true` to signal the restoration LaunchedEffect.
     * 2. Forces `isInitialScrollDone = true` to prevent the effect from loading "stale" 
     *    progress from the DataStore for the chapter being navigated into.
     * 3. `shouldScrollToBottom` determines if we snap to the start or end of the new chapter.
     */
    fun navigateNext() {
        if (currentChapterIndex < book.spineHrefs.size - 1) {
            shouldScrollToBottom = false
            isGestureNavigation = true
            // [AI_CRITICAL] Force isInitialScrollDone = true to prevent the restoration effect 
            // from looking up old progress for a DIFFERENT chapter while the new one is loading.
            isInitialScrollDone = true 
            isRestoringPosition = false
            currentChapterIndex++
        }
    }

    fun navigatePrev(toBottom: Boolean = true) {
        if (currentChapterIndex > 0) {
            shouldScrollToBottom = toBottom
            isGestureNavigation = true
            // [AI_CRITICAL] Force isInitialScrollDone = true to prevent the restoration effect 
            // from looking up old progress for a DIFFERENT chapter while the new one is loading.
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
        // [AI_CRITICAL] Force isInitialScrollDone = true during scrubber interaction 
        // to prevent the save-progress effect from fighting with the user's active manual scroll.
        isInitialScrollDone = true
        isRestoringPosition = false
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && !isLoadingChapter && isInitialScrollDone) {
                    val delta = available.y
                    if (delta > 0 && !listState.canScrollBackward) {
                        verticalOverscroll = (verticalOverscroll + delta).coerceIn(0f, overscrollThreshold * 1.5f)
                        return Offset(0f, delta)
                    } else if (delta < 0 && !listState.canScrollForward) {
                        verticalOverscroll = (verticalOverscroll + delta).coerceIn(-overscrollThreshold * 1.5f, 0f)
                        return Offset(0f, delta)
                    }
                }
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && verticalOverscroll != 0f) {
                    val delta = available.y
                    if ((verticalOverscroll > 0 && delta < 0) || (verticalOverscroll < 0 && delta > 0)) {
                        val oldOverscroll = verticalOverscroll
                        verticalOverscroll = if (verticalOverscroll > 0) {
                            (verticalOverscroll + delta).coerceAtLeast(0f)
                        } else {
                            (verticalOverscroll + delta).coerceAtMost(0f)
                        }
                        return Offset(0f, verticalOverscroll - oldOverscroll)
                    }
                }
                return Offset.Zero
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = !drawerState.isOpen) {
        scope.launch { saveAndBack() }
    }

    // Scroll TOC to current chapter
    LaunchedEffect(drawerState.currentValue, currentChapterIndex) {
        if (drawerState.currentValue == DrawerValue.Open) {
            scrollTocToCurrentChapter()
        }
    }

    // Control system bar visibility
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTrigger by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                resumeTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(showControls, globalSettings.showSystemBar, resumeTrigger) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (showControls || globalSettings.showSystemBar) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            // Delay to allow the Reader Top/Bottom bars to animate out first.
            // Matches the tween(400, delayMillis = 100) exit animation in ReaderScreenChrome.
            kotlinx.coroutines.delay(450)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    val chromeState = ReaderChromeState(
        book = book,
        settings = globalSettings,
        themeColors = themeColors,
        drawerState = drawerState,
        listState = listState,
        tocListState = tocListState,
        currentChapterIndex = currentChapterIndex,
        chapterElements = chapterElements,
        isLoadingChapter = isLoadingChapter,
        showControls = showControls,
        tocSort = tocSort,
        sortedToc = sortedToc,
        verticalOverscroll = verticalOverscroll,
        overscrollThreshold = overscrollThreshold,
        nestedScrollConnection = nestedScrollConnection,
    )
    val chromeCallbacks = ReaderChromeCallbacks(
        onShowControlsChange = { showControls = it },
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
        onUpdateSettings = { transform -> scope.launch { settingsManager.updateGlobalSettings(transform) } },
        onNavigatePrev = { navigatePrev(toBottom = false) },
        onNavigateNext = ::navigateNext,
        onMainScrubberDragStart = ::handleMainScrubberDragStart
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ReaderScreenChrome(
            state = chromeState,
            callbacks = chromeCallbacks
        )
    }
}
