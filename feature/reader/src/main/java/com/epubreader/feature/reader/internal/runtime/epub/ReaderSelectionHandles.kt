package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ReaderSelectionHandleLayer(
    startHandle: ReaderSelectionHandleUiState?,
    endHandle: ReaderSelectionHandleUiState?,
    draggedHandle: ReaderSelectionHandle?,
    dragPointerInHost: Offset?,
    color: Color,
    onHandleDragStart: (ReaderSelectionHandle, Offset) -> Unit,
    onHandleDrag: (Offset) -> Unit,
    onHandleDragEnd: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        startHandle?.let { handleState ->
            ReaderSelectionHandle(
                handleState = handleState.copy(
                    anchorInHost = if (draggedHandle == ReaderSelectionHandle.Start && dragPointerInHost != null) {
                        dragPointerInHost
                    } else {
                        handleState.anchorInHost
                    },
                ),
                color = color,
                onHandleDragStart = onHandleDragStart,
                onHandleDrag = onHandleDrag,
                onHandleDragEnd = onHandleDragEnd,
            )
        }
        endHandle?.let { handleState ->
            ReaderSelectionHandle(
                handleState = handleState.copy(
                    anchorInHost = if (draggedHandle == ReaderSelectionHandle.End && dragPointerInHost != null) {
                        dragPointerInHost
                    } else {
                        handleState.anchorInHost
                    },
                ),
                color = color,
                onHandleDragStart = onHandleDragStart,
                onHandleDrag = onHandleDrag,
                onHandleDragEnd = onHandleDragEnd,
            )
        }
    }
}

@Composable
private fun ReaderSelectionHandle(
    handleState: ReaderSelectionHandleUiState,
    color: Color,
    onHandleDragStart: (ReaderSelectionHandle, Offset) -> Unit,
    onHandleDrag: (Offset) -> Unit,
    onHandleDragEnd: () -> Unit,
) {
    val touchTargetSize = 32.dp
    val density = LocalDensity.current
    val touchTargetSizePx = with(density) { touchTargetSize.toPx() }
    val handleTopLeft = Offset(
        x = handleState.anchorInHost.x - (touchTargetSizePx / 2f),
        y = handleState.anchorInHost.y - (touchTargetSizePx / 2f),
    )
    val latestHandleTopLeft by rememberUpdatedState(handleTopLeft)
    val latestAnchorInHost by rememberUpdatedState(handleState.anchorInHost)
    var pointerInHost by remember(handleState.handle) {
        mutableStateOf(
            Offset(
                x = handleState.anchorInHost.x,
                y = handleState.anchorInHost.y,
            ),
        )
    }
    var fingerOffsetFromAnchor by remember(handleState.handle) {
        mutableStateOf(Offset.Zero)
    }

    Box(
        modifier = Modifier
            .testTag(
                when (handleState.handle) {
                    ReaderSelectionHandle.Start -> "reader_selection_handle_start"
                    ReaderSelectionHandle.End -> "reader_selection_handle_end"
                },
            )
            .size(touchTargetSize)
            .offset {
                IntOffset(
                    x = handleTopLeft.x.roundToInt(),
                    y = handleTopLeft.y.roundToInt(),
                )
            }
            .pointerInput(handleState.handle) {
                detectReaderHandleDragGestures(
                    onDragStart = { localPosition ->
                        val fingerPointerInHost = Offset(
                            x = latestHandleTopLeft.x + localPosition.x,
                            y = latestHandleTopLeft.y + localPosition.y,
                        )
                        fingerOffsetFromAnchor = fingerPointerInHost - latestAnchorInHost
                        pointerInHost = latestAnchorInHost
                        onHandleDragStart(
                            handleState.handle,
                            pointerInHost,
                        )
                    },
                    onDrag = { localPosition ->
                        val fingerPointerInHost = Offset(
                            x = latestHandleTopLeft.x + localPosition.x,
                            y = latestHandleTopLeft.y + localPosition.y,
                        )
                        pointerInHost = fingerPointerInHost - fingerOffsetFromAnchor
                        onHandleDrag(pointerInHost)
                    },
                    onDragEnd = onHandleDragEnd,
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeColor = color.copy(alpha = 0.45f)
            drawLine(
                color = strokeColor,
                start = Offset(x = size.width / 2f, y = size.height * 0.15f),
                end = Offset(x = size.width / 2f, y = size.height * 0.5f),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = color,
                radius = size.minDimension * 0.22f,
                center = Offset(size.width / 2f, size.height * 0.72f),
            )
        }
    }
}
