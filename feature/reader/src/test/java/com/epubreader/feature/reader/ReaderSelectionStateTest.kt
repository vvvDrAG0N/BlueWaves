package com.epubreader.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionHandle
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionSessionPhase
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionStateTest {

    @Test
    fun shouldForceClearReaderSelectionSession_returnsTrueOnlyWhenSelectableTextIsDisabledWhileSelectionIsActive() {
        assertTrue(
            shouldForceClearReaderSelectionSession(
                selectableTextEnabled = false,
                isTextSelectionSessionActive = true,
            ),
        )

        assertFalse(
            shouldForceClearReaderSelectionSession(
                selectableTextEnabled = true,
                isTextSelectionSessionActive = true,
            ),
        )

        assertFalse(
            shouldForceClearReaderSelectionSession(
                selectableTextEnabled = false,
                isTextSelectionSessionActive = false,
            ),
        )
    }

    @Test
    fun updateDraggedHandle_swapsTheStartHandleToEndWhenTheDragCrossesPastTheSelectionEnd() {
        val state = ReaderSelectionState()
        state.activate(TextRange(2, 8))
        state.startDraggingHandle(
            handle = ReaderSelectionHandle.Start,
            pointerInHost = Offset.Zero,
        )

        state.updateDraggedHandle(11)

        assertEquals(TextRange(8, 11), state.normalizedSelection)
        assertEquals(ReaderSelectionHandle.End, state.draggedHandle)
    }

    @Test
    fun updateDraggedHandle_swapsTheEndHandleToStartWhenTheDragCrossesPastTheSelectionStart() {
        val state = ReaderSelectionState()
        state.activate(TextRange(2, 8))
        state.startDraggingHandle(
            handle = ReaderSelectionHandle.End,
            pointerInHost = Offset.Zero,
        )

        state.updateDraggedHandle(1)

        assertEquals(TextRange(1, 2), state.normalizedSelection)
        assertEquals(ReaderSelectionHandle.Start, state.draggedHandle)
    }

    @Test
    fun clear_resetsTheSessionPhaseToIdle() {
        val state = ReaderSelectionState()

        state.startSelectionGesture(TextRange(1, 4))
        state.clear()

        assertEquals(ReaderSelectionSessionPhase.Idle, state.phase)
        assertTrue(state.isIdle)
        assertFalse(state.isActive)
    }

    @Test
    fun resolveSessionPhase_returnsIdleWhenTheSelectionCanNoLongerExtractText() {
        val state = ReaderSelectionState()

        state.activate(TextRange(1, 4))

        assertEquals(ReaderSelectionSessionPhase.Idle, state.resolveSessionPhase(selectedTextLength = 0))
        assertFalse(state.hasUsableSelection(selectedTextLength = 0))
    }

    @Test
    fun finishSelectionGesture_promotesGestureSelectionToActiveSelection() {
        val state = ReaderSelectionState()

        state.startSelectionGesture(TextRange(1, 4))
        state.finishSelectionGesture()

        assertEquals(ReaderSelectionSessionPhase.ActiveSelection, state.resolveSessionPhase(selectedTextLength = 3))
        assertTrue(state.hasUsableSelection(selectedTextLength = 3))
    }
}
