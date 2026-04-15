package com.epubreader.app

import com.epubreader.core.model.BookProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationEditProgressTest {

    @Test
    fun repairProgressAfterBookEdit_keepsExistingHref() {
        val original = listOf("chapter1.xhtml", "chapter2.xhtml", "chapter3.xhtml")
        val updated = listOf("chapter1.xhtml", "chapter2.xhtml", "chapter4.xhtml")
        val previous = BookProgress(
            scrollIndex = 7,
            scrollOffset = 42,
            lastChapterHref = "chapter2.xhtml",
        )

        val repaired = repairProgressAfterBookEdit(
            previousProgress = previous,
            originalSpineHrefs = original,
            updatedSpineHrefs = updated,
        )

        assertEquals(previous, repaired)
    }

    @Test
    fun repairProgressAfterBookEdit_movesForwardWhenCurrentChapterWasDeleted() {
        val repaired = repairProgressAfterBookEdit(
            previousProgress = BookProgress(
                scrollIndex = 4,
                scrollOffset = 8,
                lastChapterHref = "chapter2.xhtml",
            ),
            originalSpineHrefs = listOf("chapter1.xhtml", "chapter2.xhtml", "chapter3.xhtml"),
            updatedSpineHrefs = listOf("chapter1.xhtml", "chapter3.xhtml", "chapter4.xhtml"),
        )

        assertEquals(BookProgress(lastChapterHref = "chapter3.xhtml"), repaired)
    }

    @Test
    fun repairProgressAfterBookEdit_movesBackwardWhenDeletedChapterWasLastRemainingNeighbor() {
        val repaired = repairProgressAfterBookEdit(
            previousProgress = BookProgress(
                scrollIndex = 1,
                scrollOffset = 2,
                lastChapterHref = "chapter3.xhtml",
            ),
            originalSpineHrefs = listOf("chapter1.xhtml", "chapter2.xhtml", "chapter3.xhtml"),
            updatedSpineHrefs = listOf("chapter1.xhtml", "chapter2.xhtml"),
        )

        assertEquals(BookProgress(lastChapterHref = "chapter2.xhtml"), repaired)
    }

    @Test
    fun repairProgressAfterBookEdit_fallsBackToFirstUpdatedChapterForUnknownHref() {
        val repaired = repairProgressAfterBookEdit(
            previousProgress = BookProgress(
                scrollIndex = 9,
                scrollOffset = 10,
                lastChapterHref = "missing.xhtml",
            ),
            originalSpineHrefs = listOf("chapter1.xhtml", "chapter2.xhtml"),
            updatedSpineHrefs = listOf("chapter4.xhtml", "chapter5.xhtml"),
        )

        assertEquals(BookProgress(lastChapterHref = "chapter4.xhtml"), repaired)
    }
}
