package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange

@Stable
internal class ReaderSelectionState {
    var phase by mutableStateOf(ReaderSelectionSessionPhase.Idle)
        private set

    var selection by mutableStateOf<TextRange?>(null)
        private set

    var draggedHandle by mutableStateOf<ReaderSelectionHandle?>(null)
        private set

    var dragPointerInHost by mutableStateOf<Offset?>(null)
        private set

    var dragSource by mutableStateOf<ReaderSelectionDragSource?>(null)
        private set

    val isActive: Boolean
        get() = selection?.let { !it.collapsed } == true

    val isHandleDragActive: Boolean
        get() = draggedHandle != null

    val normalizedSelection: TextRange?
        get() = selection?.normalized

    val isIdle: Boolean
        get() = phase == ReaderSelectionSessionPhase.Idle

    fun resolveSessionPhase(selectedTextLength: Int): ReaderSelectionSessionPhase {
        if (!isActive || selectedTextLength <= 0) {
            return ReaderSelectionSessionPhase.Idle
        }
        return when {
            draggedHandle != null -> ReaderSelectionSessionPhase.HandleDragging
            phase == ReaderSelectionSessionPhase.GestureSelecting -> ReaderSelectionSessionPhase.GestureSelecting
            else -> ReaderSelectionSessionPhase.ActiveSelection
        }
    }

    fun hasUsableSelection(selectedTextLength: Int): Boolean {
        return resolveSessionPhase(selectedTextLength) != ReaderSelectionSessionPhase.Idle
    }

    fun activate(range: TextRange) {
        val didActivate = updateSelection(range)
        phase = if (didActivate) ReaderSelectionSessionPhase.ActiveSelection else ReaderSelectionSessionPhase.Idle
        clearDragState()
    }

    fun startSelectionGesture(range: TextRange) {
        val didActivate = updateSelection(range)
        phase = if (didActivate) ReaderSelectionSessionPhase.GestureSelecting else ReaderSelectionSessionPhase.Idle
        clearDragState()
    }

    fun finishSelectionGesture() {
        if (draggedHandle != null && dragSource == ReaderSelectionDragSource.SelectionGesture) {
            finishHandleDrag()
            return
        }
        phase = if (isActive) ReaderSelectionSessionPhase.ActiveSelection else ReaderSelectionSessionPhase.Idle
    }

    fun clear() {
        selection = null
        clearDragState()
        phase = ReaderSelectionSessionPhase.Idle
    }

    fun startDraggingHandle(
        handle: ReaderSelectionHandle,
        pointerInHost: Offset,
        source: ReaderSelectionDragSource = ReaderSelectionDragSource.Handle,
    ) {
        if (!isActive) {
            return
        }
        draggedHandle = handle
        dragPointerInHost = pointerInHost
        dragSource = source
        phase = ReaderSelectionSessionPhase.HandleDragging
    }

    fun updateDragPointer(pointerInHost: Offset) {
        dragPointerInHost = pointerInHost
    }

    fun updateDraggedHandle(newDocumentOffset: Int) {
        val currentSelection = normalizedSelection ?: return
        when (draggedHandle) {
            ReaderSelectionHandle.Start -> {
                if (newDocumentOffset <= currentSelection.end) {
                    selection = TextRange(newDocumentOffset, currentSelection.end)
                } else {
                    selection = TextRange(currentSelection.end, newDocumentOffset)
                    draggedHandle = ReaderSelectionHandle.End
                }
            }

            ReaderSelectionHandle.End -> {
                if (newDocumentOffset >= currentSelection.start) {
                    selection = TextRange(currentSelection.start, newDocumentOffset)
                } else {
                    selection = TextRange(newDocumentOffset, currentSelection.start)
                    draggedHandle = ReaderSelectionHandle.Start
                }
            }

            null -> Unit
        }
        if (draggedHandle != null) {
            phase = ReaderSelectionSessionPhase.HandleDragging
        }
    }

    fun finishHandleDrag() {
        clearDragState()
        phase = if (isActive) ReaderSelectionSessionPhase.ActiveSelection else ReaderSelectionSessionPhase.Idle
    }

    private fun updateSelection(range: TextRange): Boolean {
        val normalizedRange = range.normalized
        selection = if (normalizedRange.collapsed) null else normalizedRange
        return selection != null
    }

    private fun clearDragState() {
        draggedHandle = null
        dragPointerInHost = null
        dragSource = null
    }
}

internal enum class ReaderSelectionSessionPhase {
    Idle,
    GestureSelecting,
    ActiveSelection,
    HandleDragging,
}

internal enum class ReaderSelectionDragSource {
    SelectionGesture,
    Handle,
}
