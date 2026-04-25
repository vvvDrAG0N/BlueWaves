package com.epubreader.feature.reader.internal.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun VerticalScrubber(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onDragStart: () -> Unit = {},
    isTOC: Boolean = false,
) {
    val requestScrollToItem = rememberConflatedScrollToItem(listState)
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val totalHeight = with(density) { maxHeight.toPx() }
        val thumbHeight = with(density) { 48.dp.toPx() }

        val scrollProgress by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val totalItems = layoutInfo.totalItemsCount

                if (visibleItems.isNotEmpty() && totalItems > 0) {
                    val viewportStart = layoutInfo.viewportStartOffset
                    val topItem = visibleItems.firstOrNull { it.offset + it.size > viewportStart } ?: visibleItems.first()
                    val itemTop = topItem.offset
                    val relativeOffset = (viewportStart - itemTop).coerceAtLeast(0)
                    val itemHeight = if (topItem.size > 0) topItem.size else 1
                    ((topItem.index.toFloat() + (relativeOffset.toFloat() / itemHeight.toFloat())) / totalItems.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
        }

        val thumbOffset by remember {
            derivedStateOf {
                (totalHeight - thumbHeight) * scrollProgress.coerceIn(0f, 1f)
            }
        }

        val showScrubber by remember {
            derivedStateOf { listState.layoutInfo.totalItemsCount > 1 }
        }

        if (showScrubber) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (isTOC) 16.dp else 20.dp)
                    .testTag(if (isTOC) "reader_toc_vertical_scrubber" else "reader_vertical_scrubber")
                    .pointerInput(totalHeight) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, _ ->
                                change.consume()
                                val y = change.position.y
                                val progress = (y / totalHeight).coerceIn(0f, 1f)
                                val currentTotalItems = listState.layoutInfo.totalItemsCount
                                val targetIndex = (progress * currentTotalItems).toInt().coerceIn(0, currentTotalItems - 1)
                                requestScrollToItem(targetIndex)
                            },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = with(density) { thumbOffset.toDp() })
                        .width(if (isTOC) 6.dp else 4.dp)
                        .height(with(density) { thumbHeight.toDp() })
                        .background(color.copy(alpha = 0.5f), CircleShape),
                )
            }
        }
    }
}

@Composable
internal fun rememberConflatedScrollToItem(
    listState: LazyListState,
): (Int) -> Unit {
    val scrollRequests = remember(listState) {
        MutableSharedFlow<Int>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }

    LaunchedEffect(listState, scrollRequests) {
        scrollRequests.collectLatest { targetIndex ->
            listState.scrollToItem(targetIndex)
        }
    }

    return remember(scrollRequests) {
        { targetIndex -> scrollRequests.tryEmit(targetIndex) }
    }
}
