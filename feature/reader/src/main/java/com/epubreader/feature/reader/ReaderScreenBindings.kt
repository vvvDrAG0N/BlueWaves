package com.epubreader.feature.reader.internal.shell

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.State
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.TocItem
import com.epubreader.core.ui.GlobalSettingsTransform
import com.epubreader.feature.reader.ReaderChapterSection
import com.epubreader.feature.reader.ReaderChromeCallbacks
import com.epubreader.feature.reader.ReaderChromeState
import com.epubreader.feature.reader.ReaderOverlayHost
import com.epubreader.feature.reader.ReaderTheme
import com.epubreader.feature.reader.ReaderToolHost
import com.epubreader.feature.reader.TocSort

internal fun buildReaderChromeState(
    book: EpubBook,
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    drawerState: DrawerState,
    listState: LazyListState,
    tocListState: LazyListState,
    currentChapterIndex: Int,
    chapterElements: List<ChapterElement>,
    chapterSections: List<ReaderChapterSection>,
    renderedItemCount: Int,
    isLoadingChapter: Boolean,
    showControls: Boolean,
    isTextSelectionSessionActive: Boolean,
    tocSort: TocSort,
    sortedToc: List<TocItem>,
    verticalOverscrollState: State<Float>,
    overscrollThreshold: Float,
    nestedScrollConnection: NestedScrollConnection,
    progressPercentageState: State<Float>,
    selectionSessionEpoch: Int,
    overlayHosts: List<ReaderOverlayHost>,
    toolHosts: List<ReaderToolHost>,
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
        chapterSections = chapterSections,
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
        overlayHosts = overlayHosts,
        toolHosts = toolHosts,
    )
}

internal fun buildReaderChromeCallbacks(
    onShowControlsChange: (Boolean) -> Unit,
    onTextSelectionActiveChange: (Int, Boolean) -> Unit,
    onSelectionHandleDragChange: (Int, Boolean) -> Unit,
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
    onLookupSheetVisibilityChange: (Boolean) -> Unit = {},
    onLookupSheetDismissed: () -> Unit = {},
): ReaderChromeCallbacks {
    return ReaderChromeCallbacks(
        onShowControlsChange = onShowControlsChange,
        onTextSelectionActiveChange = onTextSelectionActiveChange,
        onSelectionHandleDragChange = onSelectionHandleDragChange,
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
        onLookupSheetVisibilityChange = onLookupSheetVisibilityChange,
        onLookupSheetDismissed = onLookupSheetDismissed,
    )
}
