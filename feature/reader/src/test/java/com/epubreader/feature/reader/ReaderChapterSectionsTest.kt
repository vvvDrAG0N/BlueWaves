package com.epubreader.feature.reader

import com.epubreader.core.model.ChapterElement
import com.epubreader.feature.reader.internal.runtime.epub.ReaderChapterSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChapterSectionsTest {

    @Test
    fun buildReaderChapterSections_mergesConsecutiveBodyTextIntoOneSection() {
        val sections = buildReaderChapterSections(
            chapterElements = listOf(
                ChapterElement.Text("Alpha", id = "p1"),
                ChapterElement.Text("Beta", id = "p2"),
            ),
        )

        assertEquals(1, sections.size)
        val section = sections.single() as ReaderChapterSection.TextSection
        assertEquals("text:p1:p2", section.id)
        assertEquals(listOf("p1", "p2"), section.blocks.map { it.id })
        assertEquals("Alpha".length + 2 + "Beta".length, section.charCount)
    }

    @Test
    fun buildReaderChapterSections_imagesSplitBodyTextRuns() {
        val sections = buildReaderChapterSections(
            chapterElements = listOf(
                ChapterElement.Text("Alpha", id = "p1"),
                ChapterElement.Text("Beta", id = "p2"),
                ChapterElement.Image("/tmp/image.png", id = "img1"),
                ChapterElement.Text("Gamma", id = "p3"),
            ),
        )

        assertEquals(
            listOf("text:p1:p2", "image:img1", "text:p3:p3"),
            sections.map { it.id },
        )
        assertTrue(sections[1] is ReaderChapterSection.ImageSection)
    }

    @Test
    fun buildReaderChapterSections_headingsAreIsolatedIntoStandaloneSections() {
        val sections = buildReaderChapterSections(
            chapterElements = listOf(
                ChapterElement.Text("Intro", id = "p1"),
                ChapterElement.Text("Chapter One", type = "h", id = "h1"),
                ChapterElement.Text("Body", id = "p2"),
            ),
        )

        assertEquals(
            listOf("text:p1:p1", "text:h1:h1", "text:p2:p2"),
            sections.map { it.id },
        )
        val headingSection = sections[1] as ReaderChapterSection.TextSection
        assertEquals(listOf("h1"), headingSection.blocks.map { it.id })
    }

    @Test
    fun buildReaderChapterSections_oversizedBodyRunSplitsOnlyAtParagraphBoundaries() {
        val sections = buildReaderChapterSections(
            chapterElements = listOf(
                ChapterElement.Text("123456", id = "p1"),
                ChapterElement.Text("abcdef", id = "p2"),
                ChapterElement.Text("uvwxyz", id = "p3"),
            ),
            softCharLimit = 10,
        )

        assertEquals(
            listOf("text:p1:p1", "text:p2:p2", "text:p3:p3"),
            sections.map { it.id },
        )
    }

    @Test
    fun buildReaderChapterSections_singleOversizedParagraphRemainsInOneSection() {
        val sections = buildReaderChapterSections(
            chapterElements = listOf(
                ChapterElement.Text("123456789012", id = "p1"),
            ),
            softCharLimit = 10,
        )

        assertEquals(1, sections.size)
        val section = sections.single() as ReaderChapterSection.TextSection
        assertEquals("text:p1:p1", section.id)
        assertEquals(listOf("p1"), section.blocks.map { it.id })
    }

    @Test
    fun buildReaderChapterSections_idsStayStableFromParserElementIds() {
        val sections = buildReaderChapterSections(
            chapterElements = listOf(
                ChapterElement.Text("Alpha", id = "alpha"),
                ChapterElement.Image("/tmp/cover.png", id = "cover"),
                ChapterElement.Text("Omega", id = "omega"),
            ),
        )

        assertEquals(
            listOf("text:alpha:alpha", "image:cover", "text:omega:omega"),
            sections.map { it.id },
        )
    }
}
