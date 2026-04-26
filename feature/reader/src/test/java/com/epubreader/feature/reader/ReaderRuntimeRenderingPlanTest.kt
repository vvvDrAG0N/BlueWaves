package com.epubreader.feature.reader

import com.epubreader.core.model.ChapterElement
import com.epubreader.feature.reader.internal.runtime.epub.buildReaderChapterSections
import com.epubreader.feature.reader.internal.runtime.epub.buildReaderRuntimeRenderingPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReaderRuntimeRenderingPlanTest {

    @Test
    fun buildReaderRuntimeRenderingPlan_reusesSectionsAndSkipsSelectionDocumentWhenSelectableTextIsDisabled() {
        val chapterSections = buildReaderChapterSections(
            chapterElements = listOf(
                ChapterElement.Text("Alpha", id = "p1"),
                ChapterElement.Image("/tmp/cover.png", id = "img1"),
                ChapterElement.Text("Beta", id = "p2"),
            ),
        )

        val plan = buildReaderRuntimeRenderingPlan(
            chapterSections = chapterSections,
            selectableTextEnabled = false,
        )

        assertSame(chapterSections, plan.chapterSections)
        assertNull(plan.selectionDocument)
    }

    @Test
    fun buildReaderRuntimeRenderingPlan_buildsSelectionDocumentWhenSelectableTextIsEnabled() {
        val chapterSections = buildReaderChapterSections(
            chapterElements = listOf(
                ChapterElement.Text("Alpha", id = "p1"),
                ChapterElement.Image("/tmp/cover.png", id = "img1"),
                ChapterElement.Text("Beta", id = "p2"),
            ),
        )

        val plan = buildReaderRuntimeRenderingPlan(
            chapterSections = chapterSections,
            selectableTextEnabled = true,
        )

        assertSame(chapterSections, plan.chapterSections)
        assertNotNull(plan.selectionDocument)
        assertEquals(2, plan.selectionDocument?.sections?.size)
    }
}
