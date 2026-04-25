package com.epubreader.feature.reader.internal.shell

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.EpubBook
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import com.epubreader.feature.reader.internal.runtime.epub.ReaderChapterSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal suspend fun saveReaderProgressSnapshot(
    book: com.epubreader.core.model.EpubBook,
    settingsManager: SettingsManager,
    currentChapterIndex: Int,
    listState: LazyListState,
    chapterSections: List<ReaderChapterSection>,
    isInitialScrollDone: Boolean,
    isRestoringPosition: Boolean,
    isGestureNavigation: Boolean,
    skipRestoration: Boolean,
    shouldScrollToBottom: Boolean,
) {
    if (currentChapterIndex == -1 || currentChapterIndex >= book.spineHrefs.size) {
        return
    }

    withContext(NonCancellable) {
        val progress = when {
            isInitialScrollDone && !isRestoringPosition -> {
                BookProgress(
                    scrollIndex = chapterSections
                        .getOrNull(listState.firstVisibleItemIndex)
                        ?.startElementIndex
                        ?: 0,
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                    lastChapterHref = book.spineHrefs[currentChapterIndex],
                )
            }

            isGestureNavigation || skipRestoration -> {
                BookProgress(
                    scrollIndex = if (shouldScrollToBottom) Int.MAX_VALUE else 0,
                    scrollOffset = 0,
                    lastChapterHref = book.spineHrefs[currentChapterIndex],
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

internal suspend fun scrollReaderTocToCurrentChapter(
    currentChapterIndex: Int,
    book: com.epubreader.core.model.EpubBook,
    sortedToc: List<com.epubreader.core.model.TocItem>,
    tocListState: LazyListState,
) {
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

internal suspend fun loadReaderChapterElements(
    parser: EpubParser,
    book: EpubBook,
    chapterIndex: Int,
): List<ChapterElement> {
    if (chapterIndex !in book.spineHrefs.indices) {
        return emptyList()
    }

    return withContext(Dispatchers.IO) {
        parser.parseChapter(book.rootPath, book.spineHrefs[chapterIndex])
    }
}

internal fun shouldPrefetchAdjacentReaderChapters(
    currentChapterIndex: Int,
    spineSize: Int,
    hasChapterElements: Boolean,
    isLoadingChapter: Boolean,
    isChapterSettleComplete: Boolean,
    hasReaderUserInteracted: Boolean,
): Boolean {
    return currentChapterIndex in 0 until spineSize &&
        spineSize > 1 &&
        hasChapterElements &&
        !isLoadingChapter &&
        isChapterSettleComplete &&
        hasReaderUserInteracted
}

internal suspend fun prefetchAdjacentReaderChapters(
    parser: EpubParser,
    book: EpubBook,
    chapterIndex: Int,
) {
    if (chapterIndex !in book.spineHrefs.indices) {
        return
    }

    withContext(Dispatchers.IO) {
        if (chapterIndex > 0) {
            parser.parseChapter(book.rootPath, book.spineHrefs[chapterIndex - 1])
        }
        if (chapterIndex < book.spineHrefs.size - 1) {
            parser.parseChapter(book.rootPath, book.spineHrefs[chapterIndex + 1])
        }
    }
}

internal fun mapReaderProgressIndexToRenderedItem(
    progressIndex: Int,
    chapterSections: List<ReaderChapterSection>,
): Int {
    if (chapterSections.isEmpty()) {
        return 0
    }

    return chapterSections.indexOfFirst { section ->
        progressIndex in section.startElementIndex..section.endElementIndex
    }.takeIf { it != -1 } ?: chapterSections.lastIndex
}

internal fun mapRenderedItemIndexToReaderProgressIndex(
    renderedItemIndex: Int,
    chapterSections: List<ReaderChapterSection>,
): Int {
    return chapterSections.getOrNull(renderedItemIndex)?.startElementIndex ?: 0
}

@Composable
internal fun rememberReaderNestedScrollConnection(
    listState: LazyListState,
    isLoadingChapter: Boolean,
    isInitialScrollDone: Boolean,
    overscrollThreshold: Float,
    verticalOverscroll: Float,
    onVerticalOverscrollChange: (Float) -> Unit,
): NestedScrollConnection {
    return remember(listState, isLoadingChapter, isInitialScrollDone, overscrollThreshold, verticalOverscroll) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && !isLoadingChapter && isInitialScrollDone) {
                    val delta = available.y
                    if (delta > 0 && !listState.canScrollBackward) {
                        onVerticalOverscrollChange((verticalOverscroll + delta).coerceIn(0f, overscrollThreshold * 1.5f))
                        return Offset(0f, delta)
                    } else if (delta < 0 && !listState.canScrollForward) {
                        onVerticalOverscrollChange((verticalOverscroll + delta).coerceIn(-overscrollThreshold * 1.5f, 0f))
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
                        val nextOverscroll = if (verticalOverscroll > 0) {
                            (verticalOverscroll + delta).coerceAtLeast(0f)
                        } else {
                            (verticalOverscroll + delta).coerceAtMost(0f)
                        }
                        onVerticalOverscrollChange(nextOverscroll)
                        return Offset(0f, nextOverscroll - oldOverscroll)
                    }
                }
                return Offset.Zero
            }
        }
    }
}
