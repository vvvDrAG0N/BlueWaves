package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.runtime.Immutable
import com.epubreader.core.model.ChapterElement

internal const val ReaderTextSectionSoftCharLimit = 500
private const val ReaderTextSectionParagraphSeparatorCharCount = 2

@Immutable
internal sealed interface ReaderChapterSection {
    val id: String
    val startElementIndex: Int
    val endElementIndex: Int

    @Immutable
    data class TextSection(
        override val id: String,
        override val startElementIndex: Int,
        override val endElementIndex: Int,
        val blocks: List<ChapterElement.Text>,
        val charCount: Int,
    ) : ReaderChapterSection

    @Immutable
    data class ImageSection(
        override val id: String,
        override val startElementIndex: Int,
        override val endElementIndex: Int,
        val image: ChapterElement.Image,
    ) : ReaderChapterSection
}

internal fun buildReaderChapterSections(
    chapterElements: List<ChapterElement>,
    softCharLimit: Int = ReaderTextSectionSoftCharLimit,
): List<ReaderChapterSection> {
    val sections = mutableListOf<ReaderChapterSection>()
    val bodyBlocks = mutableListOf<ChapterElement.Text>()
    var bodyCharCount = 0
    var bodyStartIndex = -1
    var bodyEndIndex = -1

    fun flushBodyBlocks() {
        if (bodyBlocks.isEmpty()) {
            return
        }
        val firstId = bodyBlocks.first().id
        val lastId = bodyBlocks.last().id
        sections += ReaderChapterSection.TextSection(
            id = "text:$firstId:$lastId",
            startElementIndex = bodyStartIndex,
            endElementIndex = bodyEndIndex,
            blocks = bodyBlocks.toList(),
            charCount = bodyCharCount,
        )
        bodyBlocks.clear()
        bodyCharCount = 0
        bodyStartIndex = -1
        bodyEndIndex = -1
    }

    chapterElements.forEachIndexed { elementIndex, element ->
        when (element) {
            is ChapterElement.Image -> {
                flushBodyBlocks()
                sections += ReaderChapterSection.ImageSection(
                    id = "image:${element.id}",
                    startElementIndex = elementIndex,
                    endElementIndex = elementIndex,
                    image = element,
                )
            }

            is ChapterElement.Text -> {
                if (element.isHeading()) {
                    flushBodyBlocks()
                    sections += ReaderChapterSection.TextSection(
                        id = "text:${element.id}:${element.id}",
                        startElementIndex = elementIndex,
                        endElementIndex = elementIndex,
                        blocks = listOf(element),
                        charCount = element.content.length,
                    )
                    return@forEachIndexed
                }

                val separatorCharCount = if (bodyBlocks.isEmpty()) 0 else ReaderTextSectionParagraphSeparatorCharCount
                val candidateCharCount = bodyCharCount + separatorCharCount + element.content.length
                if (bodyBlocks.isNotEmpty() && candidateCharCount > softCharLimit) {
                    flushBodyBlocks()
                }

                val appliedSeparatorCharCount =
                    if (bodyBlocks.isEmpty()) 0 else ReaderTextSectionParagraphSeparatorCharCount
                if (bodyBlocks.isEmpty()) {
                    bodyStartIndex = elementIndex
                }
                bodyBlocks += element
                bodyEndIndex = elementIndex
                bodyCharCount += appliedSeparatorCharCount + element.content.length
            }
        }
    }

    flushBodyBlocks()
    return sections
}

private fun ChapterElement.Text.isHeading(): Boolean = type == "h"
