package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult

@Stable
internal class ReaderSelectionLayoutRegistry {
    private val visibleLayouts = mutableStateMapOf<String, ReaderVisibleSectionLayout>()

    fun update(
        snapshot: ReaderVisibleSectionLayout,
    ) {
        visibleLayouts[snapshot.sectionId] = snapshot
    }

    fun remove(sectionId: String) {
        visibleLayouts.remove(sectionId)
    }

    fun resolvePositionInSection(
        sectionId: String,
        localPositionInSection: Offset,
    ): ReaderResolvedSelectionPosition? {
        val layout = visibleLayouts[sectionId] ?: return null
        val clampedLocalPosition = Offset(
            x = localPositionInSection.x.coerceIn(0f, layout.boundsInHost.width),
            y = localPositionInSection.y.coerceIn(0f, layout.boundsInHost.height),
        )
        val localOffset = layout.textLayoutResult
            .getOffsetForPosition(clampedLocalPosition)
            .coerceIn(0, layout.textLength)
        return ReaderResolvedSelectionPosition(
            sectionId = sectionId,
            localOffset = localOffset,
            documentOffset = layout.documentStart + localOffset,
            positionInHost = Offset(
                x = layout.boundsInHost.left + clampedLocalPosition.x,
                y = layout.boundsInHost.top + clampedLocalPosition.y,
            ),
        )
    }

    fun resolvePositionFromSectionPointer(
        sectionId: String,
        localPositionInSection: Offset,
    ): ReaderResolvedSelectionPosition? {
        val layout = visibleLayouts[sectionId] ?: return null
        val pointerInHost = Offset(
            x = layout.boundsInHost.left + localPositionInSection.x,
            y = layout.boundsInHost.top + localPositionInSection.y,
        )
        return if (
            pointerInHost.x < layout.boundsInHost.left ||
            pointerInHost.x > layout.boundsInHost.right ||
            pointerInHost.y < layout.boundsInHost.top ||
            pointerInHost.y > layout.boundsInHost.bottom
        ) {
            resolvePositionInVisibleSections(pointerInHost)
        } else {
            resolvePositionInSection(
                sectionId = sectionId,
                localPositionInSection = localPositionInSection,
            )
        }
    }

    fun resolvePositionInVisibleSections(
        positionInHost: Offset,
    ): ReaderResolvedSelectionPosition? {
        val visibleOrderedLayouts = visibleLayouts.values.sortedBy { it.documentStart }
        if (visibleOrderedLayouts.isEmpty()) {
            return null
        }

        val targetLayout = visibleOrderedLayouts.firstOrNull { layout ->
            positionInHost.y in layout.boundsInHost.top..layout.boundsInHost.bottom
        } ?: when {
            positionInHost.y < visibleOrderedLayouts.first().boundsInHost.top -> visibleOrderedLayouts.first()
            positionInHost.y > visibleOrderedLayouts.last().boundsInHost.bottom -> visibleOrderedLayouts.last()
            else -> visibleOrderedLayouts.minByOrNull { layout ->
                when {
                    positionInHost.y < layout.boundsInHost.top -> layout.boundsInHost.top - positionInHost.y
                    positionInHost.y > layout.boundsInHost.bottom -> positionInHost.y - layout.boundsInHost.bottom
                    else -> 0f
                }
            }
        } ?: return null

        return resolvePositionInSection(
            sectionId = targetLayout.sectionId,
            localPositionInSection = Offset(
                x = positionInHost.x - targetLayout.boundsInHost.left,
                y = positionInHost.y - targetLayout.boundsInHost.top,
            ),
        )
    }

    fun resolveHandleAnchor(
        offset: Int,
        affinity: ReaderSelectionOffsetAffinity,
        document: ReaderSelectionDocument,
    ): Offset? {
        val section = document.sectionForOffset(offset, affinity) ?: return null
        val layout = visibleLayouts[section.sectionId] ?: return null
        val localOffset = (offset - section.documentStart).coerceIn(0, layout.textLength)
        val cursorRect = layout.textLayoutResult.getCursorRect(localOffset)

        val localAnchor = when (affinity) {
            ReaderSelectionOffsetAffinity.Downstream -> Offset(cursorRect.left, cursorRect.bottom)
            ReaderSelectionOffsetAffinity.Upstream -> Offset(cursorRect.right, cursorRect.bottom)
        }

        return Offset(
            x = layout.boundsInHost.left + localAnchor.x,
            y = layout.boundsInHost.top + localAnchor.y,
        )
    }
}

internal data class ReaderVisibleSectionLayout(
    val sectionId: String,
    val boundsInHost: Rect,
    val textLayoutResult: TextLayoutResult,
    val textLength: Int,
    val documentStart: Int,
)

internal data class ReaderResolvedSelectionPosition(
    val sectionId: String,
    val localOffset: Int,
    val documentOffset: Int,
    val positionInHost: Offset,
)
