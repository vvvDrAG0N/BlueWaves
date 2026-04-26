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
        val localOffset = resolveParagraphSeamOffset(
            layout = layout,
            localPositionInSection = clampedLocalPosition,
        ) ?: resolveRenderedTextBoundaryOffset(
            layout = layout,
            localPositionInSection = clampedLocalPosition,
        ) ?: layout.textLayoutResult
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
        resolveInterSectionSeamPosition(
            visibleOrderedLayouts = visibleOrderedLayouts,
            positionInHost = positionInHost,
        )?.let { return it }

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

    fun resolveVisibleTextVerticalBounds(): ReaderVisibleTextVerticalBounds? {
        val visibleOrderedLayouts = visibleLayouts.values.sortedBy { it.documentStart }
        if (visibleOrderedLayouts.isEmpty()) {
            return null
        }
        return ReaderVisibleTextVerticalBounds(
            top = visibleOrderedLayouts.first().renderedTextTopInHost,
            bottom = visibleOrderedLayouts.last().renderedTextBottomInHost,
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
        val shouldUseUpstreamCursorAnchor = affinity == ReaderSelectionOffsetAffinity.Upstream &&
            shouldUseReaderUpstreamCursorAnchor(
                text = layout.text,
                localOffset = localOffset,
                textLength = layout.textLength,
            )
        val anchorCharacterOffset = when (affinity) {
            ReaderSelectionOffsetAffinity.Downstream -> localOffset.coerceAtMost(layout.textLength - 1)
            ReaderSelectionOffsetAffinity.Upstream -> if (shouldUseUpstreamCursorAnchor) {
                null
            } else {
                resolveUpstreamHandleAnchorCharacterOffset(
                    text = layout.text,
                    localOffset = localOffset,
                )
            }
        }.takeIf { layout.textLength > 0 }
        val anchorCharacterBox = anchorCharacterOffset?.let(layout.textLayoutResult::getBoundingBox)

        val localAnchor = when (affinity) {
            ReaderSelectionOffsetAffinity.Downstream -> Offset(
                x = anchorCharacterBox?.left ?: cursorRect.left,
                y = resolveReaderSelectionHandleAnchorY(
                    cursorRect = cursorRect,
                    affinity = affinity,
                ),
            )
            ReaderSelectionOffsetAffinity.Upstream -> Offset(
                x = anchorCharacterBox?.right ?: cursorRect.right,
                y = anchorCharacterOffset?.let { visualAnchorCharacterOffset ->
                    layout.textLayoutResult.getLineBottom(
                        layout.textLayoutResult.getLineForOffset(visualAnchorCharacterOffset),
                    )
                } ?: resolveReaderSelectionHandleAnchorY(
                    cursorRect = cursorRect,
                    affinity = affinity,
                ),
            )
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
    val text: String,
    val paragraphStartOffsets: List<Int>,
    val textLayoutResult: TextLayoutResult,
    val textLength: Int,
    val documentStart: Int,
    val renderedTextTopInSection: Float,
    val renderedTextBottomInSection: Float,
)

internal data class ReaderResolvedSelectionPosition(
    val sectionId: String,
    val localOffset: Int,
    val documentOffset: Int,
    val positionInHost: Offset,
)

private fun resolveParagraphSeamOffset(
    layout: ReaderVisibleSectionLayout,
    localPositionInSection: Offset,
): Int? {
    if (layout.textLayoutResult.lineCount == 0 || layout.paragraphStartOffsets.size < 2) {
        return null
    }

    layout.paragraphStartOffsets.drop(1).forEach { paragraphStartOffset ->
        if (paragraphStartOffset !in 1 until layout.textLength) {
            return@forEach
        }

        val previousParagraphContentOffset =
            (paragraphStartOffset - ReaderSelectionParagraphSeparator.length - 1).coerceAtLeast(0)
        val previousContentLineIndex = layout.textLayoutResult.getLineForOffset(
            previousParagraphContentOffset.coerceAtMost(layout.textLength - 1),
        )
        val nextContentLineIndex = layout.textLayoutResult.getLineForOffset(
            paragraphStartOffset.coerceAtMost(layout.textLength - 1),
        )
        val seamTop = layout.textLayoutResult.getLineBottom(previousContentLineIndex)
        val seamBottom = layout.textLayoutResult.getLineTop(nextContentLineIndex)
        if (seamBottom >= seamTop && localPositionInSection.y in seamTop..seamBottom) {
            return paragraphStartOffset
        }
    }

    return null
}

private fun resolveRenderedTextBoundaryOffset(
    layout: ReaderVisibleSectionLayout,
    localPositionInSection: Offset,
): Int? {
    if (localPositionInSection.y < layout.renderedTextTopInSection) {
        return 0
    }
    if (localPositionInSection.y > layout.renderedTextBottomInSection) {
        return layout.textLength
    }
    return null
}

private fun resolveInterSectionSeamPosition(
    visibleOrderedLayouts: List<ReaderVisibleSectionLayout>,
    positionInHost: Offset,
): ReaderResolvedSelectionPosition? {
    for (index in 0 until visibleOrderedLayouts.lastIndex) {
        val currentLayout = visibleOrderedLayouts[index]
        val nextLayout = visibleOrderedLayouts[index + 1]
        val seamTop = currentLayout.renderedTextBottomInHost
        val seamBottom = nextLayout.renderedTextTopInHost
        if (positionInHost.y <= seamTop || positionInHost.y >= seamBottom) {
            continue
        }

        return ReaderResolvedSelectionPosition(
            sectionId = nextLayout.sectionId,
            localOffset = 0,
            documentOffset = nextLayout.documentStart,
            positionInHost = positionInHost,
        )
    }
    return null
}

private val ReaderVisibleSectionLayout.renderedTextTopInHost: Float
    get() = boundsInHost.top + renderedTextTopInSection

private val ReaderVisibleSectionLayout.renderedTextBottomInHost: Float
    get() = boundsInHost.top + renderedTextBottomInSection

private fun resolveUpstreamHandleAnchorCharacterOffset(
    text: String,
    localOffset: Int,
): Int {
    var candidateOffset = (localOffset - 1).coerceAtLeast(0)
    while (candidateOffset > 0 && text[candidateOffset].isReaderParagraphSeparatorCharacter()) {
        candidateOffset--
    }
    return candidateOffset
}

private fun Char.isReaderParagraphSeparatorCharacter(): Boolean {
    return this == '\n' || this == '\r'
}

private fun shouldUseReaderUpstreamCursorAnchor(
    text: String,
    localOffset: Int,
    textLength: Int,
): Boolean {
    if (textLength == 0) {
        return true
    }
    if (localOffset == textLength) {
        return true
    }
    if (localOffset <= 0) {
        return false
    }
    return text[localOffset - 1].isWhitespace()
}
