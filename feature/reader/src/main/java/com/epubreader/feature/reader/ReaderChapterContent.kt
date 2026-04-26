package com.epubreader.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.internal.runtime.epub.EpubReaderRuntime

@Composable
internal fun ReaderChapterContent(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterElements: List<ChapterElement>,
    chapterSections: List<ReaderChapterSection> = buildReaderChapterSections(chapterElements),
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
    selectionSessionEpoch: Int = 0,
    onSelectionActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
    onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    onLookupSheetVisibilityChange: (Boolean) -> Unit = {},
    onLookupSheetDismissed: () -> Unit = {},
) {
    EpubReaderRuntime(
        settings = settings,
        themeColors = themeColors,
        listState = listState,
        chapterElements = chapterElements,
        isLoadingChapter = isLoadingChapter,
        currentChapterIndex = currentChapterIndex,
        selectionSessionEpoch = selectionSessionEpoch,
        onSelectionActiveChange = onSelectionActiveChange,
        onSelectionHandleDragChange = onSelectionHandleDragChange,
        onLookupSheetVisibilityChange = onLookupSheetVisibilityChange,
        onLookupSheetDismissed = onLookupSheetDismissed,
    )
}
