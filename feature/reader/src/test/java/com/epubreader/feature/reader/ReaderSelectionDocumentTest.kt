package com.epubreader.feature.reader

import androidx.compose.ui.text.TextRange
import com.epubreader.core.model.ChapterElement
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionParagraphSeparator
import com.epubreader.feature.reader.internal.runtime.epub.buildReaderChapterSections
import com.epubreader.feature.reader.internal.runtime.epub.buildReaderSelectionDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSelectionDocumentTest {

    @Test
    fun buildReaderSelectionDocument_omitsImageSectionsFromTheSelectableDocument() {
        val sections = buildReaderChapterSections(
            chapterElements = listOf(
                ChapterElement.Text("Alpha", id = "p1"),
                ChapterElement.Image("/tmp/cover.png", id = "img1"),
                ChapterElement.Text("Beta", id = "p2"),
            ),
        )

        val document = buildReaderSelectionDocument(sections)

        assertEquals(2, document.sections.size)
        assertNull(document.sectionById("image:img1"))
        assertEquals("Alpha", document.sections[0].text)
        assertEquals("Beta", document.sections[1].text)
    }

    @Test
    fun rangeInSection_returnsLocalRangeForASelectionThatSpansMultipleSections() {
        val sections = buildReaderChapterSections(
            chapterElements = listOf(
                ChapterElement.Text("Alpha", id = "p1"),
                ChapterElement.Image("/tmp/cover.png", id = "img1"),
                ChapterElement.Text("Beta", id = "p2"),
            ),
        )
        val document = buildReaderSelectionDocument(sections)
        val firstSection = document.sections[0]
        val secondSection = document.sections[1]

        val selection = TextRange(
            start = firstSection.documentStart + 2,
            end = secondSection.documentStart + 2,
        )

        assertEquals(TextRange(2, 5), document.rangeInSection(firstSection.sectionId, selection))
        assertEquals(TextRange(0, 2), document.rangeInSection(secondSection.sectionId, selection))
    }

    @Test
    fun extractSelectedText_joinsVisibleTextSectionsWithStableParagraphSeparators() {
        val sections = buildReaderChapterSections(
            chapterElements = listOf(
                ChapterElement.Text("Alpha", id = "p1"),
                ChapterElement.Image("/tmp/cover.png", id = "img1"),
                ChapterElement.Text("Beta", id = "p2"),
            ),
        )
        val document = buildReaderSelectionDocument(sections)
        val firstSection = document.sections[0]
        val secondSection = document.sections[1]

        val selection = TextRange(
            start = firstSection.documentStart,
            end = secondSection.documentEnd,
        )

        assertEquals(
            "Alpha${ReaderSelectionParagraphSeparator}Beta",
            document.extractSelectedText(selection),
        )
    }
}
