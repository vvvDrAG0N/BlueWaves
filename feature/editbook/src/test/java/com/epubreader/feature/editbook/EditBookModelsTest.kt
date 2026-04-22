package com.epubreader.feature.editbook

import com.epubreader.core.model.BookNewChapterContent
import org.junit.Assert.assertEquals
import org.junit.Test

class EditBookModelsTest {

    @Test
    fun selectChapterRange_selectsInclusiveRange() {
        val chapters = listOf(
            buildTextDraftChapter("One", "Body 1"),
            buildTextDraftChapter("Two", "Body 2"),
            buildTextDraftChapter("Three", "Body 3"),
            buildTextDraftChapter("Four", "Body 4"),
        )

        val selected = selectChapterRange(chapters, startPosition = 2, endPosition = 3)

        assertEquals(listOf(chapters[1].id, chapters[2].id), selected.toList())
    }

    @Test
    fun selectOutsideChapterRange_selectsEverythingOutsideBounds() {
        val chapters = listOf(
            buildTextDraftChapter("One", "Body 1"),
            buildTextDraftChapter("Two", "Body 2"),
            buildTextDraftChapter("Three", "Body 3"),
            buildTextDraftChapter("Four", "Body 4"),
        )

        val selected = selectOutsideChapterRange(chapters, startPosition = 2, endPosition = 3)

        assertEquals(listOf(chapters[0].id, chapters[3].id), selected.toList())
    }

    @Test
    fun selectSpecificChapter_selectsOnlyRequestedIndex() {
        val chapters = listOf(
            buildTextDraftChapter("One", "Body 1"),
            buildTextDraftChapter("Two", "Body 2"),
            buildTextDraftChapter("Three", "Body 3"),
        )

        val selected = selectSpecificChapter(chapters, position = 2)

        assertEquals(listOf(chapters[1].id), selected.toList())
    }

    @Test
    fun moveSelectedChapterItems_movesBlockToRequestedPosition() {
        val chapters = listOf(
            buildTextDraftChapter("One", "Body 1"),
            buildTextDraftChapter("Two", "Body 2"),
            buildTextDraftChapter("Three", "Body 3"),
            buildTextDraftChapter("Four", "Body 4"),
        )

        val moved = moveSelectedChapterItems(
            currentItems = chapters,
            selectedIds = linkedSetOf(chapters[1].id, chapters[2].id),
            targetPosition = 1,
        )

        assertEquals(listOf("Two", "Three", "One", "Four"), moved.map(EditableChapterItem::title))
    }

    @Test
    fun inferImportedChapterTitle_usesHtmlTitleBeforeFilename() {
        val title = inferImportedChapterTitle(
            fileNameHint = "chapter-file.xhtml",
            markup = """<html><head><title>Imported Bonus</title></head><body><p>Body</p></body></html>""",
        )

        assertEquals("Imported Bonus", title)
    }

    @Test
    fun matchesChapterSearch_supportsExactIndexQueries() {
        val chapter = EditableChapterItem(
            id = "chapter-700",
            title = "Appendix",
            href = "OEBPS/appendix.xhtml",
            content = null,
            isPersisted = true,
            source = EditableChapterSource.EXISTING,
        )

        assertEquals(true, matchesChapterSearch(chapter, position = 700, query = "700"))
        assertEquals(false, matchesChapterSearch(chapter, position = 700, query = "70"))
    }

    @Test
    fun buildHtmlDraftChapter_keepsHtmlContentType() {
        val chapter = buildHtmlDraftChapter(
            title = "Imported",
            markup = "<p>Hello</p>",
            fileNameHint = "imported.xhtml",
        )

        assertEquals(EditableChapterSource.IMPORTED_HTML, chapter.source)
        assertEquals(
            BookNewChapterContent.HtmlDocument("<p>Hello</p>", "imported.xhtml"),
            chapter.content,
        )
    }
}
