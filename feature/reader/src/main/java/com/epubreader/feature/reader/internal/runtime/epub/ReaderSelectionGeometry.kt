package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
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
    handleTextHitBiasPx: Float,
): Offset {
    return when (dragSource) {
        ReaderSelectionDragSource.Handle -> pointerInHost.copy(
            y = pointerInHost.y - handleTextHitBiasPx,
        )

        else -> pointerInHost
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
