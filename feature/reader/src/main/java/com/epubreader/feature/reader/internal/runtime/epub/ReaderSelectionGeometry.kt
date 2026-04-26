package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs

internal enum class ReaderSelectionHandle {
    Start,
    End,
}

internal enum class ReaderSelectionOffsetAffinity {
    Downstream,
    Upstream,
}

@Immutable
internal data class ReaderSelectionHandleUiState(
    val handle: ReaderSelectionHandle,
    val anchorInHost: Offset,
)

@Immutable
internal data class ReaderVisibleTextVerticalBounds(
    val top: Float,
    val bottom: Float,
)

@Immutable
internal data class ReaderSelectionHandleDimensions(
    val knobRadiusPx: Float,
    val stemWidthPx: Float,
    val stemHeightPx: Float,
    val textClearancePx: Float,
    val minimumTouchTargetWidthPx: Float,
    val minimumTouchTargetHeightPx: Float,
)

@Immutable
internal data class ReaderSelectionHandleLayout(
    val touchTargetWidthPx: Float,
    val touchTargetHeightPx: Float,
    val handleTopLeft: Offset,
    val handleCenterXInHandle: Float,
    val stemTopYInHandle: Float,
    val stemBottomYInHandle: Float,
    val knobCenterYInHandle: Float,
) {
    val stemHeightPx: Float
        get() = stemBottomYInHandle - stemTopYInHandle

    val stemTopYInHost: Float
        get() = handleTopLeft.y + stemTopYInHandle

    val stemBottomYInHost: Float
        get() = handleTopLeft.y + stemBottomYInHandle

    val knobCenterYInHost: Float
        get() = handleTopLeft.y + knobCenterYInHandle
}

internal val TextRange.normalized: TextRange
    get() = if (start <= end) this else TextRange(end, start)

internal fun normalizeReaderSelectionRange(
    start: Int,
    end: Int,
): TextRange {
    return if (start <= end) TextRange(start, end) else TextRange(end, start)
}

internal fun findReaderWordBoundary(
    text: String,
    rawOffset: Int,
): TextRange {
    if (text.isEmpty()) {
        return TextRange.Zero
    }

    val targetIndex = resolveNearestReaderWordIndex(
        text = text,
        rawOffset = rawOffset,
    ) ?: rawOffset.coerceIn(0, text.lastIndex)

    val breakIterator = BreakIterator.getWordInstance(Locale.getDefault())
    breakIterator.setText(text)

    var start = breakIterator.preceding(targetIndex + 1)
    if (start == BreakIterator.DONE) {
        start = 0
    }
    var end = breakIterator.following(targetIndex)
    if (end == BreakIterator.DONE) {
        end = text.length
    }

    while (start < end && !text[start].isReaderSelectableWordCharacter()) {
        start++
    }
    while (end > start && !text[end - 1].isReaderSelectableWordCharacter()) {
        end--
    }

    return when {
        start < end -> TextRange(start, end)
        else -> {
            val fallbackEnd = (targetIndex + 1).coerceAtMost(text.length)
            TextRange(targetIndex, fallbackEnd)
        }
    }
}

internal fun snapReaderSelectionOffsetToWordBoundary(
    text: String,
    rawOffset: Int,
    handle: ReaderSelectionHandle,
): Int {
    val wordBoundary = findReaderWordBoundary(text, rawOffset)
    return when (handle) {
        ReaderSelectionHandle.Start -> wordBoundary.start
        ReaderSelectionHandle.End -> wordBoundary.end
    }
}

internal fun resolveReaderSelectionTargetPointer(
    pointerInHost: Offset,
    dragSource: ReaderSelectionDragSource?,
): Offset {
    return when (dragSource) {
        ReaderSelectionDragSource.Handle,
        ReaderSelectionDragSource.SelectionGesture,
        null,
        -> pointerInHost
    }
}

internal fun clampReaderSelectionHandlePointer(
    pointerInHost: Offset,
    hostWidth: Float,
    hostHeight: Float,
    handleInsetPx: Float,
): Offset {
    if (hostWidth <= 0f || hostHeight <= 0f) {
        return pointerInHost
    }
    val minX = handleInsetPx.coerceAtLeast(0f)
    val minY = handleInsetPx.coerceAtLeast(0f)
    val maxX = (hostWidth - handleInsetPx).coerceAtLeast(minX)
    val maxY = (hostHeight - handleInsetPx).coerceAtLeast(minY)
    return Offset(
        x = pointerInHost.x.coerceIn(minX, maxX),
        y = pointerInHost.y.coerceIn(minY, maxY),
    )
}

internal fun clampReaderSelectionHandlePointerToSafeArea(
    pointerInHost: Offset,
    hostWidth: Float,
    hostHeight: Float,
    handleInsetPx: Float,
    safeVerticalInsetPx: Float,
): Offset {
    val edgeClampedPointer = clampReaderSelectionHandlePointer(
        pointerInHost = pointerInHost,
        hostWidth = hostWidth,
        hostHeight = hostHeight,
        handleInsetPx = handleInsetPx,
    )
    if (hostHeight <= 0f || safeVerticalInsetPx <= handleInsetPx || hostHeight <= safeVerticalInsetPx * 2f) {
        return edgeClampedPointer
    }
    val minY = safeVerticalInsetPx.coerceAtLeast(handleInsetPx)
    val maxY = (hostHeight - safeVerticalInsetPx).coerceAtLeast(minY)
    return edgeClampedPointer.copy(
        y = edgeClampedPointer.y.coerceIn(minY, maxY),
    )
}

internal fun clampReaderSelectionHandlePointerToVisibleSafeArea(
    pointerInHost: Offset,
    hostWidth: Float,
    hostHeight: Float,
    handle: ReaderSelectionHandle,
    handleInsetPx: Float,
    safeVerticalInsetPx: Float,
    visualHeightPx: Float,
    textClearancePx: Float,
): Offset {
    val edgeClampedPointer = clampReaderSelectionHandlePointer(
        pointerInHost = pointerInHost,
        hostWidth = hostWidth,
        hostHeight = hostHeight,
        handleInsetPx = handleInsetPx,
    )
    if (hostHeight <= 0f || safeVerticalInsetPx <= 0f) {
        return edgeClampedPointer
    }
    val minY = when (handle) {
        ReaderSelectionHandle.Start -> safeVerticalInsetPx + textClearancePx + visualHeightPx
        ReaderSelectionHandle.End -> safeVerticalInsetPx - textClearancePx
    }.coerceAtLeast(handleInsetPx)
    val maxY = when (handle) {
        ReaderSelectionHandle.Start -> hostHeight - safeVerticalInsetPx + textClearancePx
        ReaderSelectionHandle.End -> hostHeight - safeVerticalInsetPx - textClearancePx - visualHeightPx
    }.coerceAtLeast(minY)
    return edgeClampedPointer.copy(
        y = edgeClampedPointer.y.coerceIn(minY, maxY),
    )
}

internal fun resolveReaderSelectionHandleVisualCenterX(
    baseCenterX: Float,
    handle: ReaderSelectionHandle,
    stemWidthPx: Float,
): Float {
    val stemHalfWidth = stemWidthPx / 2f
    return when (handle) {
        ReaderSelectionHandle.Start -> baseCenterX - stemHalfWidth
        ReaderSelectionHandle.End -> baseCenterX + stemHalfWidth
    }
}

internal fun resolveReaderSelectionHandleAnchorY(
    cursorRect: Rect,
    affinity: ReaderSelectionOffsetAffinity,
): Float {
    return when (affinity) {
        ReaderSelectionOffsetAffinity.Downstream -> cursorRect.top
        ReaderSelectionOffsetAffinity.Upstream -> cursorRect.bottom
    }
}

internal fun resolveReaderSelectionHandleLayout(
    handle: ReaderSelectionHandle,
    textAnchorInHost: Offset,
    dimensions: ReaderSelectionHandleDimensions,
): ReaderSelectionHandleLayout {
    val knobDiameterPx = dimensions.knobRadiusPx * 2f
    val touchTargetWidthPx = dimensions.minimumTouchTargetWidthPx.coerceAtLeast(knobDiameterPx)
    val visualHeightPx = knobDiameterPx + dimensions.stemHeightPx
    val touchTargetHeightPx = dimensions.minimumTouchTargetHeightPx.coerceAtLeast(visualHeightPx)
    val visualTopInsetPx = (touchTargetHeightPx - visualHeightPx) / 2f
    val handleCenterXInHandle = resolveReaderSelectionHandleVisualCenterX(
        baseCenterX = touchTargetWidthPx / 2f,
        handle = handle,
        stemWidthPx = dimensions.stemWidthPx,
    )

    val stemTopYInHandle: Float
    val stemBottomYInHandle: Float
    val knobCenterYInHandle: Float
    val handleTopLeftY: Float

    when (handle) {
        ReaderSelectionHandle.Start -> {
            stemTopYInHandle = visualTopInsetPx + knobDiameterPx
            stemBottomYInHandle = stemTopYInHandle + dimensions.stemHeightPx
            knobCenterYInHandle = visualTopInsetPx + dimensions.knobRadiusPx
            handleTopLeftY =
                textAnchorInHost.y - dimensions.textClearancePx - (visualTopInsetPx + visualHeightPx)
        }

        ReaderSelectionHandle.End -> {
            stemTopYInHandle = visualTopInsetPx
            stemBottomYInHandle = stemTopYInHandle + dimensions.stemHeightPx
            knobCenterYInHandle = stemBottomYInHandle + dimensions.knobRadiusPx
            handleTopLeftY = textAnchorInHost.y + dimensions.textClearancePx - visualTopInsetPx
        }
    }

    return ReaderSelectionHandleLayout(
        touchTargetWidthPx = touchTargetWidthPx,
        touchTargetHeightPx = touchTargetHeightPx,
        handleTopLeft = Offset(
            x = textAnchorInHost.x - (touchTargetWidthPx / 2f),
            y = handleTopLeftY,
        ),
        handleCenterXInHandle = handleCenterXInHandle,
        stemTopYInHandle = stemTopYInHandle,
        stemBottomYInHandle = stemBottomYInHandle,
        knobCenterYInHandle = knobCenterYInHandle,
    )
}

internal fun projectReaderSelectionHandleTargetPointer(
    pointerInHost: Offset,
    visibleTextBounds: ReaderVisibleTextVerticalBounds?,
    dragSource: ReaderSelectionDragSource?,
): Offset? {
    val bounds = visibleTextBounds ?: return null
    if (bounds.bottom <= bounds.top) {
        return null
    }
    val targetPointer = resolveReaderSelectionTargetPointer(
        pointerInHost = pointerInHost,
        dragSource = dragSource,
    )
    val maxProjectedY = (bounds.bottom - 1f).coerceAtLeast(bounds.top)
    return targetPointer.copy(
        y = targetPointer.y.coerceIn(bounds.top, maxProjectedY),
    )
}

internal fun resolveReaderSelectionAutoScrollDelta(
    pointerY: Float?,
    hostHeight: Float,
    edgeZonePx: Float,
    hasValidResolvedTarget: Boolean,
): Float {
    if (pointerY == null || !hasValidResolvedTarget || hostHeight <= 0f || edgeZonePx <= 0f) {
        return 0f
    }

    return when {
        pointerY < edgeZonePx -> {
            val strength = (1f - (pointerY / edgeZonePx)).coerceIn(0f, 1f)
            -36f * strength
        }

        pointerY > hostHeight - edgeZonePx -> {
            val distanceIntoEdge = pointerY - (hostHeight - edgeZonePx)
            val strength = (distanceIntoEdge / edgeZonePx).coerceIn(0f, 1f)
            36f * strength
        }

        else -> 0f
    }
}

private fun resolveNearestReaderWordIndex(
    text: String,
    rawOffset: Int,
): Int? {
    if (text.isEmpty()) {
        return null
    }

    val clampedOffset = rawOffset.coerceIn(0, text.lastIndex)
    if (text[clampedOffset].isReaderSelectableWordCharacter()) {
        return clampedOffset
    }

    var leftIndex = clampedOffset - 1
    var rightIndex = clampedOffset + 1
    while (leftIndex >= 0 || rightIndex < text.length) {
        val leftDistance = if (leftIndex >= 0) abs(clampedOffset - leftIndex) else Int.MAX_VALUE
        val rightDistance = if (rightIndex < text.length) abs(rightIndex - clampedOffset) else Int.MAX_VALUE

        if (leftDistance <= rightDistance && leftIndex >= 0) {
            if (text[leftIndex].isReaderSelectableWordCharacter()) {
                return leftIndex
            }
            leftIndex--
        } else if (rightIndex < text.length) {
            if (text[rightIndex].isReaderSelectableWordCharacter()) {
                return rightIndex
            }
            rightIndex++
        }
    }

    return null
}

private fun Char.isReaderSelectableWordCharacter(): Boolean {
    return Character.isLetterOrDigit(this)
}
