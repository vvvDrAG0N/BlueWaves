package com.epubreader.feature.editbook

import com.epubreader.core.model.BookProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class EditBookLegoPluginTest {

    @Test
    fun repairEditBookProgress_keepsMatchingHrefWhenItStillExists() {
        val repaired = repairEditBookProgress(
            previousProgress = BookProgress(
                scrollIndex = 7,
                scrollOffset = 13,
                lastChapterHref = "Text/chapter-2.xhtml#fragment",
            ),
            originalSpineHrefs = listOf("Text/chapter-1.xhtml", "Text/chapter-2.xhtml"),
            updatedSpineHrefs = listOf("Text/chapter-1.xhtml", "Text/chapter-2.xhtml"),
        )

        assertEquals(
            BookProgress(
                scrollIndex = 7,
                scrollOffset = 13,
                lastChapterHref = "Text/chapter-2.xhtml",
            ),
            repaired,
        )
    }

    @Test
    fun repairEditBookProgress_fallsForwardThenBackwardWhenChapterWasRemoved() {
        val repaired = repairEditBookProgress(
            previousProgress = BookProgress(lastChapterHref = "Text/chapter-2.xhtml"),
            originalSpineHrefs = listOf(
                "Text/chapter-1.xhtml",
                "Text/chapter-2.xhtml",
                "Text/chapter-3.xhtml",
            ),
            updatedSpineHrefs = listOf(
                "Text/chapter-1.xhtml",
                "Text/chapter-3.xhtml",
            ),
        )

        assertEquals(BookProgress(lastChapterHref = "Text/chapter-3.xhtml"), repaired)
    }
}
