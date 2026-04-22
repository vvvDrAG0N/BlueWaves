package com.epubreader.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.State
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.TocItem

internal fun buildReaderChromeState(
    book: EpubBook,
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    drawerState: DrawerState,
    listState: LazyListState,
    tocListState: LazyListState,
    currentChapterIndex: Int,
    chapterElements: List<ChapterElement>,
    isLoadingChapter: Boolean,
    showControls: Boolean,
    isTextSelectionSessionActive: Boolean,
    tocSort: TocSort,
    sortedToc: List<TocItem>,
    verticalOverscrollState: State<Float>,
    overscrollThreshold: Float,
    nestedScrollConnection: NestedScrollConnection,
    progressPercentageState: State<Float>,
    selectionResetToken: Int,
): ReaderChromeState {
    return ReaderChromeState(
        book = book,
        settings = settings,
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
}

internal fun buildReaderChromeCallbacks(
    onShowControlsChange: (Boolean) -> Unit,
    onTextSelectionActiveChange: (Boolean) -> Unit,
    onClearTextSelection: () -> Unit,
    onToggleTocSort: () -> Unit,
    onReleaseOverscroll: () -> Unit,
    onSaveAndBack: () -> Unit,
    onOpenToc: () -> Unit,
    onCloseToc: () -> Unit,
    onLocateCurrentChapterInToc: () -> Unit,
    onJumpToChapter: (Int) -> Unit,
    onSelectTocChapter: (Int) -> Unit,
    onPreviewSettings: (GlobalSettingsTransform) -> Unit,
    onPersistSettings: (GlobalSettingsTransform) -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    onMainScrubberDragStart: () -> Unit,
): ReaderChromeCallbacks {
    return ReaderChromeCallbacks(
        onShowControlsChange = onShowControlsChange,
        onTextSelectionActiveChange = onTextSelectionActiveChange,
        onClearTextSelection = onClearTextSelection,
        onToggleTocSort = onToggleTocSort,
        onReleaseOverscroll = onReleaseOverscroll,
        onSaveAndBack = onSaveAndBack,
        onOpenToc = onOpenToc,
        onCloseToc = onCloseToc,
        onLocateCurrentChapterInToc = onLocateCurrentChapterInToc,
        onJumpToChapter = onJumpToChapter,
        onSelectTocChapter = onSelectTocChapter,
        onPreviewSettings = onPreviewSettings,
        onPersistSettings = onPersistSettings,
        onNavigatePrev = onNavigatePrev,
        onNavigateNext = onNavigateNext,
        onMainScrubberDragStart = onMainScrubberDragStart,
    )
}
