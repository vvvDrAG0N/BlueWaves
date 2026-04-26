package com.epubreader.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionDragSource
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionHandle
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionHandleDimensions
import com.epubreader.feature.reader.internal.runtime.epub.ReaderVisibleTextVerticalBounds
import com.epubreader.feature.reader.internal.runtime.epub.clampReaderSelectionHandlePointer
import com.epubreader.feature.reader.internal.runtime.epub.clampReaderSelectionHandlePointerToSafeArea
import com.epubreader.feature.reader.internal.runtime.epub.clampReaderSelectionHandlePointerToVisibleSafeArea
import com.epubreader.feature.reader.internal.runtime.epub.findReaderWordBoundary
import com.epubreader.feature.reader.internal.runtime.epub.normalizeReaderSelectionRange
import com.epubreader.feature.reader.internal.runtime.epub.projectReaderSelectionHandleTargetPointer
import com.epubreader.feature.reader.internal.runtime.epub.resolveReaderSelectionAutoScrollDelta
import com.epubreader.feature.reader.internal.runtime.epub.resolveReaderSelectionHandleAnchorY
import com.epubreader.feature.reader.internal.runtime.epub.resolveReaderSelectionHandleDragGeometry
import com.epubreader.feature.reader.internal.runtime.epub.resolveReaderSelectionHandleLayout
import com.epubreader.feature.reader.internal.runtime.epub.resolveReaderSelectionHandleVisualCenterX
import com.epubreader.feature.reader.internal.runtime.epub.resolveReaderSelectionTargetPointer
import com.epubreader.feature.reader.internal.runtime.epub.snapReaderSelectionOffsetToWordBoundary
import com.epubreader.feature.reader.internal.runtime.epub.shouldClampReaderSelectionDragPointer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun resolveReaderSelectionTargetPointer_keepsHandleAndGesturePointersUnchanged() {
        assertEquals(
            Offset(24f, 72f),
            resolveReaderSelectionTargetPointer(
                pointerInHost = Offset(24f, 72f),
                dragSource = ReaderSelectionDragSource.Handle,
            ),
        )
        assertEquals(
            Offset(24f, 72f),
            resolveReaderSelectionTargetPointer(
                pointerInHost = Offset(24f, 72f),
                dragSource = ReaderSelectionDragSource.SelectionGesture,
            ),
        )
    }

    @Test
    fun shouldClampReaderSelectionDragPointer_onlyForActiveDragSources() {
        assertTrue(shouldClampReaderSelectionDragPointer(ReaderSelectionDragSource.Handle))
        assertTrue(shouldClampReaderSelectionDragPointer(ReaderSelectionDragSource.SelectionGesture))
        assertFalse(shouldClampReaderSelectionDragPointer(null))
    }

    @Test
    fun resolveReaderSelectionHandleDragGeometry_buildsTheVisualPickupPointInHost() {
        val geometry = resolveReaderSelectionHandleDragGeometry(
            textAnchorInHost = Offset(120f, 200f),
            handleTopLeft = Offset(80f, 140f),
            visualPickupPointInHandle = Offset(14f, 18f),
        )

        assertEquals(Offset(120f, 200f), geometry.textAnchorInHost)
        assertEquals(Offset(94f, 158f), geometry.visualPickupPointInHost)
        assertEquals(Offset(26f, 42f), geometry.textAnchorOffsetFromVisualPickup)
    }

    @Test
    fun resolveReaderSelectionHandleDragGeometry_resolvesLogicalAnchorFromTheDraggedVisualPickup() {
        val geometry = resolveReaderSelectionHandleDragGeometry(
            textAnchorInHost = Offset(160f, 300f),
            handleTopLeft = Offset(118f, 228f),
            visualPickupPointInHandle = Offset(12f, 14f),
        )

        assertEquals(
            Offset(160f, 300f),
            geometry.resolveLogicalTextAnchorPointer(
                fingerPointerInHost = geometry.visualPickupPointInHost,
                fingerOffsetFromVisualPickup = Offset.Zero,
            ),
        )
        assertEquals(
            Offset(178f, 324f),
            geometry.resolveLogicalTextAnchorPointer(
                fingerPointerInHost = Offset(148f, 266f),
                fingerOffsetFromVisualPickup = Offset.Zero,
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

    @Test
    fun clampReaderSelectionHandlePointerToSafeArea_limitsTheVisibleTearBeforeTopAndBottomEdges() {
        assertEquals(
            Offset(150f, 72f),
            clampReaderSelectionHandlePointerToSafeArea(
                pointerInHost = Offset(150f, 18f),
                hostWidth = 300f,
                hostHeight = 500f,
                handleInsetPx = 16f,
                safeVerticalInsetPx = 72f,
            ),
        )
        assertEquals(
            Offset(150f, 428f),
            clampReaderSelectionHandlePointerToSafeArea(
                pointerInHost = Offset(150f, 492f),
                hostWidth = 300f,
                hostHeight = 500f,
                handleInsetPx = 16f,
                safeVerticalInsetPx = 72f,
            ),
        )
    }

    @Test
    fun clampReaderSelectionHandlePointerToVisibleSafeArea_keepsRenderedHandlesOutsideTheThreeLineBand() {
        assertEquals(
            Offset(150f, 99f),
            clampReaderSelectionHandlePointerToVisibleSafeArea(
                pointerInHost = Offset(150f, 18f),
                hostWidth = 300f,
                hostHeight = 500f,
                handle = ReaderSelectionHandle.Start,
                handleInsetPx = 16f,
                safeVerticalInsetPx = 72f,
                visualHeightPx = 23f,
                textClearancePx = 4f,
            ),
        )
        assertEquals(
            Offset(150f, 401f),
            clampReaderSelectionHandlePointerToVisibleSafeArea(
                pointerInHost = Offset(150f, 492f),
                hostWidth = 300f,
                hostHeight = 500f,
                handle = ReaderSelectionHandle.End,
                handleInsetPx = 16f,
                safeVerticalInsetPx = 72f,
                visualHeightPx = 23f,
                textClearancePx = 4f,
            ),
        )
    }

    @Test
    fun resolveReaderSelectionHandleVisualCenterX_offsetsTheStemOutwardByHalfItsWidth() {
        assertEquals(
            98f,
            resolveReaderSelectionHandleVisualCenterX(
                baseCenterX = 100f,
                handle = ReaderSelectionHandle.Start,
                stemWidthPx = 4f,
            ),
        )
        assertEquals(
            102f,
            resolveReaderSelectionHandleVisualCenterX(
                baseCenterX = 100f,
                handle = ReaderSelectionHandle.End,
                stemWidthPx = 4f,
            ),
        )
    }

    @Test
    fun resolveReaderSelectionHandleAnchorY_usesTheLineTopForStartAndLineBottomForEnd() {
        val cursorRect = Rect(
            left = 24f,
            top = 40f,
            right = 28f,
            bottom = 64f,
        )

        assertEquals(
            40f,
            resolveReaderSelectionHandleAnchorY(
                cursorRect = cursorRect,
                affinity = com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionOffsetAffinity.Downstream,
            ),
            0.001f,
        )
        assertEquals(
            64f,
            resolveReaderSelectionHandleAnchorY(
                cursorRect = cursorRect,
                affinity = com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionOffsetAffinity.Upstream,
            ),
            0.001f,
        )
    }

    @Test
    fun resolveReaderSelectionHandleLayout_mirrorsTheHandlesAroundTheSelectedText() {
        val dimensions = ReaderSelectionHandleDimensions(
            knobRadiusPx = 7f,
            stemWidthPx = 4f,
            stemHeightPx = 20f,
            textClearancePx = 6f,
            minimumTouchTargetWidthPx = 16f,
            minimumTouchTargetHeightPx = 16f,
        )
        val textAnchorInHost = Offset(120f, 200f)

        val startLayout = resolveReaderSelectionHandleLayout(
            handle = ReaderSelectionHandle.Start,
            textAnchorInHost = textAnchorInHost,
            dimensions = dimensions,
        )
        val endLayout = resolveReaderSelectionHandleLayout(
            handle = ReaderSelectionHandle.End,
            textAnchorInHost = textAnchorInHost,
            dimensions = dimensions,
        )

        assertEquals(startLayout.touchTargetHeightPx, endLayout.touchTargetHeightPx, 0.001f)
        assertEquals(startLayout.stemHeightPx, endLayout.stemHeightPx, 0.001f)
        assertEquals(20f, startLayout.stemHeightPx, 0.001f)
        assertEquals(194f, startLayout.stemBottomYInHost, 0.001f)
        assertEquals(206f, endLayout.stemTopYInHost, 0.001f)
        assertTrue(startLayout.knobCenterYInHost < startLayout.stemTopYInHost)
        assertTrue(endLayout.knobCenterYInHost > endLayout.stemBottomYInHost)
    }

    @Test
    fun projectReaderSelectionHandleTargetPointer_clampsBottomTargetsIntoVisibleText() {
        assertEquals(
            Offset(80f, 149f),
            projectReaderSelectionHandleTargetPointer(
                pointerInHost = Offset(80f, 220f),
                visibleTextBounds = ReaderVisibleTextVerticalBounds(
                    top = 40f,
                    bottom = 150f,
                ),
                dragSource = ReaderSelectionDragSource.Handle,
            ),
        )
    }

    @Test
    fun resolveReaderSelectionAutoScrollDelta_returnsZeroWithoutAValidResolvedTarget() {
        assertEquals(
            0f,
            resolveReaderSelectionAutoScrollDelta(
                pointerY = 496f,
                hostHeight = 500f,
                edgeZonePx = 72f,
                hasValidResolvedTarget = false,
            ),
        )
    }
}
