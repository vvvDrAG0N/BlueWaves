package com.epubreader.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings

@Composable
internal fun ReaderChapterContentTextView(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterElements: List<ChapterElement>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
    selectionResetToken: Int = 0,
    onSelectionActiveChange: (Boolean) -> Unit = {},
) {
    ReaderChapterContentLegacy(
        settings = settings,
        themeColors = themeColors,
        listState = listState,
        chapterElements = chapterElements,
        isLoadingChapter = isLoadingChapter,
        currentChapterIndex = currentChapterIndex,
        selectionResetToken = selectionResetToken,
        onSelectionActiveChange = onSelectionActiveChange,
    )
}
