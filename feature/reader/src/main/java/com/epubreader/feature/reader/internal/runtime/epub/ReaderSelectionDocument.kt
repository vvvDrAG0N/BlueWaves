package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange

internal const val ReaderSelectionParagraphSeparator = "\n\n"

@Immutable
internal data class ReaderSelectionDocumentSection(
    val sectionId: String,
    val sectionIndex: Int,
    val text: String,
    val paragraphStartOffsets: List<Int>,
    val isHeading: Boolean,
    val documentStart: Int,
    val documentEnd: Int,
)

@Immutable
internal data class ReaderSelectionDocument(
    val sections: List<ReaderSelectionDocumentSection>,
    val totalTextLength: Int,
) {
    fun sectionById(sectionId: String): ReaderSelectionDocumentSection? {
        return sections.firstOrNull { it.sectionId == sectionId }
    }

    fun sectionForOffset(
        offset: Int,
        affinity: ReaderSelectionOffsetAffinity,
    ): ReaderSelectionDocumentSection? {
        if (sections.isEmpty()) {
            return null
        }

        val clampedOffset = offset.coerceIn(0, totalTextLength)
        return when (affinity) {
            ReaderSelectionOffsetAffinity.Downstream -> {
                sections.firstOrNull { clampedOffset >= it.documentStart && clampedOffset < it.documentEnd }
                    ?: sections.lastOrNull()
            }

            ReaderSelectionOffsetAffinity.Upstream -> {
                sections.firstOrNull { clampedOffset > it.documentStart && clampedOffset <= it.documentEnd }
                    ?: sections.firstOrNull()
            }
        }
    }

    fun rangeInSection(
        sectionId: String,
        selection: TextRange?,
    ): TextRange? {
        val normalizedSelection = selection?.normalized ?: return null
        if (normalizedSelection.collapsed) {
            return null
        }

        val section = sectionById(sectionId) ?: return null
        val intersectionStart = maxOf(normalizedSelection.start, section.documentStart)
        val intersectionEnd = minOf(normalizedSelection.end, section.documentEnd)
        if (intersectionStart >= intersectionEnd) {
            return null
        }

        return TextRange(
            start = intersectionStart - section.documentStart,
            end = intersectionEnd - section.documentStart,
        )
    }

    fun extractSelectedText(selection: TextRange?): String {
        val normalizedSelection = selection?.normalized ?: return ""
        if (normalizedSelection.collapsed) {
            return ""
        }

        return sections.asSequence()
            .mapNotNull { section ->
                val localRange = rangeInSection(section.sectionId, normalizedSelection) ?: return@mapNotNull null
                section.text.substring(localRange.start, localRange.end)
            }
            .joinToString(separator = ReaderSelectionParagraphSeparator)
    }
}

internal fun buildReaderSelectionDocument(
    chapterSections: List<ReaderChapterSection>,
): ReaderSelectionDocument {
    val documentSections = buildList {
        var documentCursor = 0
        chapterSections.forEachIndexed { index, section ->
            when (section) {
                is ReaderChapterSection.ImageSection -> Unit

                is ReaderChapterSection.TextSection -> {
                    var sectionCursor = 0
                    val paragraphStartOffsets = buildList {
                        section.blocks.forEachIndexed { blockIndex, block ->
                            add(sectionCursor)
                            sectionCursor += block.content.length
                            if (blockIndex < section.blocks.lastIndex) {
                                sectionCursor += ReaderSelectionParagraphSeparator.length
                            }
                        }
                    }
                    val sectionText = section.blocks.joinToString(separator = ReaderSelectionParagraphSeparator) {
                        it.content
                    }
                    val sectionLength = sectionText.length
                    add(
                        ReaderSelectionDocumentSection(
                            sectionId = section.id,
                            sectionIndex = index,
                            text = sectionText,
                            paragraphStartOffsets = paragraphStartOffsets,
                            isHeading = section.blocks.firstOrNull()?.type == "h",
                            documentStart = documentCursor,
                            documentEnd = documentCursor + sectionLength,
                        ),
                    )
                    documentCursor += sectionLength
                }
            }
        }
    }

    return ReaderSelectionDocument(
        sections = documentSections,
        totalTextLength = documentSections.lastOrNull()?.documentEnd ?: 0,
    )
}
