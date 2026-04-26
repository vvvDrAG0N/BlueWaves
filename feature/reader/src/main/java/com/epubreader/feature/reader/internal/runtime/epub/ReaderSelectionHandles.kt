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
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal data class ReaderSelectionHandleSemanticsData(
    val touchTargetHeightPx: Float,
    val stemTopYInHandle: Float,
    val stemBottomYInHandle: Float,
    val knobCenterYInHandle: Float,
    val isHidden: Boolean,
)

internal val ReaderSelectionHandleSemanticsKey =
    SemanticsPropertyKey<ReaderSelectionHandleSemanticsData>("ReaderSelectionHandleSemantics")

internal var SemanticsPropertyReceiver.readerSelectionHandleSemantics by ReaderSelectionHandleSemanticsKey

@Composable
internal fun ReaderSelectionHandleLayer(
    startHandle: ReaderSelectionHandleUiState?,
    endHandle: ReaderSelectionHandleUiState?,
    draggedHandle: ReaderSelectionHandle?,
    dragPointerInHost: Offset?,
    stemHeightPx: Float,
    color: Color,
    onHandleDragStart: (ReaderSelectionHandle, Offset) -> Unit,
    onHandleDrag: (Offset) -> Unit,
    onHandleDragEnd: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        startHandle?.let { handleState ->
            val isHidden = draggedHandle == ReaderSelectionHandle.Start
            ReaderSelectionHandle(
                handleState = handleState.copy(
                    anchorInHost = if (isHidden && dragPointerInHost != null) {
                        dragPointerInHost
                    } else {
                        handleState.anchorInHost
                    },
                ),
                hidden = isHidden,
                stemHeightPx = stemHeightPx,
                color = color,
                onHandleDragStart = onHandleDragStart,
                onHandleDrag = onHandleDrag,
                onHandleDragEnd = onHandleDragEnd,
            )
        }
        endHandle?.let { handleState ->
            val isHidden = draggedHandle == ReaderSelectionHandle.End
            ReaderSelectionHandle(
                handleState = handleState.copy(
                    anchorInHost = if (isHidden && dragPointerInHost != null) {
                        dragPointerInHost
                    } else {
                        handleState.anchorInHost
                    },
                ),
                hidden = isHidden,
                stemHeightPx = stemHeightPx,
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
    hidden: Boolean,
    stemHeightPx: Float,
    color: Color,
    onHandleDragStart: (ReaderSelectionHandle, Offset) -> Unit,
    onHandleDrag: (Offset) -> Unit,
    onHandleDragEnd: () -> Unit,
) {
    val touchTargetSize = 32.dp
    val density = LocalDensity.current
    val dimensions = ReaderSelectionHandleDimensions(
        knobRadiusPx = with(density) { 7.dp.toPx() },
        stemWidthPx = with(density) { 4.dp.toPx() },
        stemHeightPx = stemHeightPx,
        textClearancePx = with(density) { 4.dp.toPx() },
        minimumTouchTargetWidthPx = with(density) { touchTargetSize.toPx() },
        minimumTouchTargetHeightPx = with(density) { touchTargetSize.toPx() },
    )
    val layout = resolveReaderSelectionHandleLayout(
        handle = handleState.handle,
        textAnchorInHost = handleState.anchorInHost,
        dimensions = dimensions,
    )
    val touchTargetWidthPx = layout.touchTargetWidthPx
    val touchTargetHeightPx = layout.touchTargetHeightPx
    val touchTargetWidthDp = with(density) { touchTargetWidthPx.toDp() }
    val touchTargetHeightDp = with(density) { touchTargetHeightPx.toDp() }
    val dragGeometry = resolveReaderSelectionHandleDragGeometry(
        textAnchorInHost = handleState.anchorInHost,
        handleTopLeft = layout.handleTopLeft,
        visualPickupPointInHandle = Offset(
            x = layout.handleCenterXInHandle,
            y = layout.knobCenterYInHandle,
        ),
    )
    val latestDragGeometry by rememberUpdatedState(dragGeometry)
    val latestHandleTopLeft by rememberUpdatedState(layout.handleTopLeft)
    var pointerInHost by remember(handleState.handle) {
        mutableStateOf(
            Offset(
                x = handleState.anchorInHost.x,
                y = handleState.anchorInHost.y,
            ),
        )
    }
    var fingerOffsetFromVisualPickup by remember(handleState.handle) {
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
            .semantics {
                readerSelectionHandleSemantics = ReaderSelectionHandleSemanticsData(
                    touchTargetHeightPx = layout.touchTargetHeightPx,
                    stemTopYInHandle = layout.stemTopYInHandle,
                    stemBottomYInHandle = layout.stemBottomYInHandle,
                    knobCenterYInHandle = layout.knobCenterYInHandle,
                    isHidden = hidden,
                )
            }
            .size(width = touchTargetWidthDp, height = touchTargetHeightDp)
            .offset {
                IntOffset(
                    x = layout.handleTopLeft.x.roundToInt(),
                    y = layout.handleTopLeft.y.roundToInt(),
                )
            }
            .pointerInput(handleState.handle) {
                detectReaderHandleDragGestures(
                    onDragStart = { localPosition ->
                        val fingerPointerInHost = Offset(
                            x = latestHandleTopLeft.x + localPosition.x,
                            y = latestHandleTopLeft.y + localPosition.y,
                        )
                        fingerOffsetFromVisualPickup =
                            fingerPointerInHost - latestDragGeometry.visualPickupPointInHost
                        pointerInHost = latestDragGeometry.resolveLogicalTextAnchorPointer(
                            fingerPointerInHost = fingerPointerInHost,
                            fingerOffsetFromVisualPickup = fingerOffsetFromVisualPickup,
                        )
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
                        pointerInHost = latestDragGeometry.resolveLogicalTextAnchorPointer(
                            fingerPointerInHost = fingerPointerInHost,
                            fingerOffsetFromVisualPickup = fingerOffsetFromVisualPickup,
                        )
                        onHandleDrag(pointerInHost)
                    },
                    onDragEnd = onHandleDragEnd,
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeColor = if (hidden) Color.Transparent else color.copy(alpha = 0.45f)
            val knobColor = if (hidden) Color.Transparent else color
            drawLine(
                color = strokeColor,
                start = Offset(x = layout.handleCenterXInHandle, y = layout.stemTopYInHandle),
                end = Offset(x = layout.handleCenterXInHandle, y = layout.stemBottomYInHandle),
                strokeWidth = dimensions.stemWidthPx,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = knobColor,
                radius = dimensions.knobRadiusPx,
                center = Offset(layout.handleCenterXInHandle, layout.knobCenterYInHandle),
            )
        }
    }
}
