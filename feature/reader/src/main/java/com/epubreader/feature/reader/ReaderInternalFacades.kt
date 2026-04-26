package com.epubreader.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.ReaderStatusUiState
import com.epubreader.feature.reader.internal.runtime.epub.ReaderChapterSection as InternalReaderChapterSection
import com.epubreader.feature.reader.internal.runtime.epub.buildReaderChapterSections as buildInternalReaderChapterSections
import com.epubreader.feature.reader.internal.shell.shouldPrefetchAdjacentReaderChapters as shouldPrefetchInternalReaderChapters
import com.epubreader.feature.reader.internal.ui.ReaderControls as InternalReaderControls
import com.epubreader.feature.reader.internal.ui.ReaderScreenChrome as InternalReaderScreenChrome
import com.epubreader.feature.reader.internal.ui.ReaderStatusOverlay as InternalReaderStatusOverlay
import com.epubreader.feature.reader.internal.ui.VerticalScrubber as InternalVerticalScrubber

internal typealias ReaderChapterSection = InternalReaderChapterSection

internal fun buildReaderChapterSections(
    chapterElements: List<ChapterElement>,
    softCharLimit: Int = com.epubreader.feature.reader.internal.runtime.epub.ReaderTextSectionSoftCharLimit,
): List<ReaderChapterSection> {
    return buildInternalReaderChapterSections(
        chapterElements = chapterElements,
        softCharLimit = softCharLimit,
    )
}

internal fun shouldPrefetchAdjacentReaderChapters(
    currentChapterIndex: Int,
    spineSize: Int,
    hasChapterElements: Boolean,
    isLoadingChapter: Boolean,
    isChapterSettleComplete: Boolean,
    hasReaderUserInteracted: Boolean,
): Boolean {
    return shouldPrefetchInternalReaderChapters(
        currentChapterIndex = currentChapterIndex,
        spineSize = spineSize,
        hasChapterElements = hasChapterElements,
        isLoadingChapter = isLoadingChapter,
        isChapterSettleComplete = isChapterSettleComplete,
        hasReaderUserInteracted = hasReaderUserInteracted,
    )
}

@Composable
internal fun ReaderChapterSelectionHost(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectionDocument: com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionDocument,
    selectionSessionEpoch: Int,
    onSelectionActiveChange: (Int, Boolean) -> Unit,
    onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    onLookupSheetDismissed: () -> Unit = {},
    content: @Composable (
        com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionController,
    ) -> Unit,
) {
    com.epubreader.feature.reader.internal.runtime.epub.ReaderChapterSelectionHost(
        settings = settings,
        themeColors = themeColors,
        listState = listState,
        selectionDocument = selectionDocument,
        selectionSessionEpoch = selectionSessionEpoch,
        onSelectionActiveChange = onSelectionActiveChange,
        onSelectionHandleDragChange = onSelectionHandleDragChange,
        onLookupSheetDismissed = onLookupSheetDismissed,
        content = content,
    )
}

@Composable
internal fun ReaderScreenChrome(
    state: ReaderChromeState,
    callbacks: ReaderChromeCallbacks,
) {
    InternalReaderScreenChrome(
        state = state,
        callbacks = callbacks,
    )
}

@Composable
internal fun ReaderControls(
    book: EpubBook,
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
    onPreviewSettingsChange: ((GlobalSettingsTransform) -> Unit)? = null,
    onPersistSettingsChange: ((GlobalSettingsTransform) -> Unit)? = null,
    themeColors: ReaderTheme,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    listState: LazyListState,
    itemCount: Int,
    currentChapterIndex: Int,
    totalChapters: Int,
    sectionLabel: String,
    progressPercentageState: State<Float>,
    onDismiss: () -> Unit,
    isVisible: Boolean = true,
    toolHosts: List<ReaderToolHost> = emptyList(),
) {
    InternalReaderControls(
        book = book,
        settings = settings,
        onSettingsChange = onSettingsChange,
        onPreviewSettingsChange = onPreviewSettingsChange,
        onPersistSettingsChange = onPersistSettingsChange,
        themeColors = themeColors,
        onNavigatePrev = onNavigatePrev,
        onNavigateNext = onNavigateNext,
        listState = listState,
        itemCount = itemCount,
        currentChapterIndex = currentChapterIndex,
        totalChapters = totalChapters,
        sectionLabel = sectionLabel,
        progressPercentageState = progressPercentageState,
        onDismiss = onDismiss,
        isVisible = isVisible,
        toolHosts = toolHosts,
    )
}

@Composable
internal fun ReaderStatusOverlay(
    uiState: ReaderStatusUiState,
    isVisible: Boolean,
    themeColors: ReaderTheme,
    chapterIndex: Int,
    maxChapters: Int,
    progressPercentageState: State<Float>,
    modifier: Modifier = Modifier,
) {
    InternalReaderStatusOverlay(
        uiState = uiState,
        isVisible = isVisible,
        themeColors = themeColors,
        chapterIndex = chapterIndex,
        maxChapters = maxChapters,
        progressPercentageState = progressPercentageState,
        modifier = modifier,
    )
}

@Composable
internal fun VerticalScrubber(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    onDragStart: () -> Unit = {},
    isTOC: Boolean = false,
) {
    InternalVerticalScrubber(
        listState = listState,
        modifier = modifier,
        color = color,
        onDragStart = onDragStart,
        isTOC = isTOC,
    )
}
