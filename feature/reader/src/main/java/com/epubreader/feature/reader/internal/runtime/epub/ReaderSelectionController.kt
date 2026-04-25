package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange

@Stable
internal class ReaderSelectionController(
    val selectionEnabled: Boolean,
    val selectionActive: Boolean,
    val selectionSessionPhase: ReaderSelectionSessionPhase,
    val isHandleDragActive: Boolean,
    val hostOriginInRoot: Offset,
    private val highlightedRangesBySection: Map<String, TextRange>,
    private val startSelectionAt: (String, Offset) -> Unit,
    private val updateSelectionGesture: (String, Offset) -> Unit,
    private val finishSelectionGesture: () -> Unit,
    private val updateSectionLayout: (ReaderVisibleSectionLayout) -> Unit,
    private val removeSectionLayout: (String) -> Unit,
    private val clearSelection: () -> Unit,
    private val startHandleDrag: (ReaderSelectionHandle, Offset) -> Unit,
    private val updateHandleDrag: (Offset) -> Unit,
    private val finishHandleDrag: () -> Unit,
) {
    fun highlightRangeForSection(sectionId: String): TextRange? = highlightedRangesBySection[sectionId]

    fun startSelectionAt(
        sectionId: String,
        localPositionInSection: Offset,
    ) {
        startSelectionAt.invoke(sectionId, localPositionInSection)
    }

    fun updateSelectionGesture(
        sectionId: String,
        localPositionInSection: Offset,
    ) {
        updateSelectionGesture.invoke(sectionId, localPositionInSection)
    }

    fun finishSelectionGesture() {
        finishSelectionGesture.invoke()
    }

    fun updateSectionLayout(snapshot: ReaderVisibleSectionLayout) {
        updateSectionLayout.invoke(snapshot)
    }

    fun removeSectionLayout(sectionId: String) {
        removeSectionLayout.invoke(sectionId)
    }

    fun clearSelection() {
        clearSelection.invoke()
    }

    fun startHandleDrag(
        handle: ReaderSelectionHandle,
        pointerInHost: Offset,
    ) {
        startHandleDrag.invoke(handle, pointerInHost)
    }

    fun updateHandleDrag(pointerInHost: Offset) {
        updateHandleDrag.invoke(pointerInHost)
    }

    fun finishHandleDrag() {
        finishHandleDrag.invoke()
    }
}
