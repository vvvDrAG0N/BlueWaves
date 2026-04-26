package com.epubreader.feature.reader.internal.runtime.epub

internal data class ReaderRuntimeRenderingPlan(
    val chapterSections: List<ReaderChapterSection>,
    val selectionDocument: ReaderSelectionDocument?,
)

internal fun buildReaderRuntimeRenderingPlan(
    chapterSections: List<ReaderChapterSection>,
    selectableTextEnabled: Boolean,
): ReaderRuntimeRenderingPlan {
    return ReaderRuntimeRenderingPlan(
        chapterSections = chapterSections,
        selectionDocument = if (selectableTextEnabled) {
            buildReaderSelectionDocument(chapterSections)
        } else {
            null
        },
    )
}
