package com.epubreader.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.ReaderContentEngine

internal enum class ReaderChapterContentDelegate {
    Legacy,
    ComposeLazyImproved,
    TextView,
}

internal fun resolveReaderChapterContentDelegate(
    engine: ReaderContentEngine,
): ReaderChapterContentDelegate {
    return when (engine) {
        ReaderContentEngine.LEGACY -> ReaderChapterContentDelegate.Legacy
        ReaderContentEngine.COMPOSE_LAZY_IMPROVED -> ReaderChapterContentDelegate.ComposeLazyImproved
        ReaderContentEngine.TEXT_VIEW -> ReaderChapterContentDelegate.TextView
    }
}

@Composable
internal fun ReaderChapterContent(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterElements: List<ChapterElement>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
    selectionResetToken: Int = 0,
    onSelectionActiveChange: (Boolean) -> Unit = {},
) {
    when (resolveReaderChapterContentDelegate(settings.readerContentEngine)) {
        ReaderChapterContentDelegate.Legacy -> ReaderChapterContentLegacy(
            settings = settings,
            themeColors = themeColors,
            listState = listState,
            chapterElements = chapterElements,
            isLoadingChapter = isLoadingChapter,
            currentChapterIndex = currentChapterIndex,
            selectionResetToken = selectionResetToken,
            onSelectionActiveChange = onSelectionActiveChange,
        )

        ReaderChapterContentDelegate.ComposeLazyImproved -> ReaderChapterContentComposeLazyImproved(
            settings = settings,
            themeColors = themeColors,
            listState = listState,
            chapterElements = chapterElements,
            isLoadingChapter = isLoadingChapter,
            currentChapterIndex = currentChapterIndex,
            selectionResetToken = selectionResetToken,
            onSelectionActiveChange = onSelectionActiveChange,
        )

        ReaderChapterContentDelegate.TextView -> ReaderChapterContentTextView(
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
}
