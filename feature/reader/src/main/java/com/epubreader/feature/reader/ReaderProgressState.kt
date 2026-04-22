package com.epubreader.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.epubreader.core.model.ChapterElement

@Composable
internal fun rememberReaderProgressPercentageState(
    listState: LazyListState,
    chapterElements: List<ChapterElement>,
): State<Float> {
    return remember(listState, chapterElements) {
        derivedStateOf {
            if (chapterElements.isEmpty()) {
                0f
            } else {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val total = layoutInfo.totalItemsCount

                if (visibleItems.isNotEmpty() && total > 0) {
                    val viewportStart = layoutInfo.viewportStartOffset
                    val topItem = visibleItems.firstOrNull { it.offset + it.size > viewportStart } ?: visibleItems.first()
                    val itemTop = topItem.offset
                    val relativeOffset = (viewportStart - itemTop).coerceAtLeast(0)
                    val itemHeight = if (topItem.size > 0) topItem.size else 1
                    ((topItem.index.toFloat() + (relativeOffset.toFloat() / itemHeight.toFloat())) / total.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
        }
    }
}
