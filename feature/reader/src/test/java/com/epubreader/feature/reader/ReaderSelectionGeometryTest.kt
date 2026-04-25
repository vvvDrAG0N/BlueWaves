package com.epubreader.feature.reader

import androidx.compose.ui.text.TextRange
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionDragSource
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionHandle
import com.epubreader.feature.reader.internal.runtime.epub.clampReaderSelectionHandlePointer
import com.epubreader.feature.reader.internal.runtime.epub.findReaderWordBoundary
import com.epubreader.feature.reader.internal.runtime.epub.normalizeReaderSelectionRange
import com.epubreader.feature.reader.internal.runtime.epub.resolveReaderSelectionTargetPointer
import com.epubreader.feature.reader.internal.runtime.epub.snapReaderSelectionOffsetToWordBoundary
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSelectionGeometryTest {

    @Test
    fun findReaderWordBoundary_returnsTheContainingWord() {
        assertEquals(
            TextRange(6, 10),
            findReaderWordBoundary(
                text = "Alpha Beta",
                rawOffset = 7,
            ),
        )
    }

    @Test
    fun findReaderWordBoundary_skipsWhitespaceAndPunctuationToTheNearestWord() {
        assertEquals(
            TextRange(0, 5),
            findReaderWordBoundary(
                text = "Alpha,  Beta!",
                rawOffset = 6,
            ),
        )
    }

    @Test
    fun snapReaderSelectionOffsetToWordBoundary_returnsStartAndEndEdgesForEachHandle() {
        val text = "Alpha Beta"

        assertEquals(
            0,
            snapReaderSelectionOffsetToWordBoundary(
                text = text,
                rawOffset = 2,
                handle = ReaderSelectionHandle.Start,
            ),
        )
        assertEquals(
            5,
            snapReaderSelectionOffsetToWordBoundary(
                text = text,
                rawOffset = 2,
                handle = ReaderSelectionHandle.End,
            ),
        )
    }

    @Test
    fun normalizeReaderSelectionRange_ordersOffsetsAscending() {
        assertEquals(
            TextRange(4, 9),
            normalizeReaderSelectionRange(start = 9, end = 4),
        )
    }

    @Test
    fun resolveReaderSelectionTargetPointer_biasesHandleDragsUpwardOnly() {
        assertEquals(
            Offset(24f, 56f),
            resolveReaderSelectionTargetPointer(
                pointerInHost = Offset(24f, 72f),
                dragSource = ReaderSelectionDragSource.Handle,
                handleTextHitBiasPx = 16f,
            ),
        )
        assertEquals(
            Offset(24f, 72f),
            resolveReaderSelectionTargetPointer(
                pointerInHost = Offset(24f, 72f),
                dragSource = ReaderSelectionDragSource.SelectionGesture,
                handleTextHitBiasPx = 16f,
            ),
        )
    }

    @Test
    fun clampReaderSelectionHandlePointer_keepsTheTearFullyInsideTheReaderHost() {
        assertEquals(
            Offset(16f, 16f),
            clampReaderSelectionHandlePointer(
                pointerInHost = Offset(-20f, -40f),
                hostWidth = 300f,
                hostHeight = 500f,
                handleInsetPx = 16f,
            ),
        )
        assertEquals(
            Offset(284f, 484f),
            clampReaderSelectionHandlePointer(
                pointerInHost = Offset(340f, 520f),
                hostWidth = 300f,
                hostHeight = 500f,
                handleInsetPx = 16f,
            ),
        )
    }
}
