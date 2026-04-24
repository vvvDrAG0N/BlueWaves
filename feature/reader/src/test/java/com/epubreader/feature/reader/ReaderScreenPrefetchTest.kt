package com.epubreader.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScreenPrefetchTest {

    @Test
    fun shouldPrefetchAdjacentReaderChapters_requiresSettledVisibleChapter() {
        assertFalse(
            shouldPrefetchAdjacentReaderChapters(
                currentChapterIndex = 2,
                spineSize = 5,
                hasChapterElements = true,
                isLoadingChapter = false,
                isChapterSettleComplete = false,
                hasReaderUserInteracted = true,
            )
        )
    }

    @Test
    fun shouldPrefetchAdjacentReaderChapters_skipsWhenCurrentChapterIsStillLoading() {
        assertFalse(
            shouldPrefetchAdjacentReaderChapters(
                currentChapterIndex = 2,
                spineSize = 5,
                hasChapterElements = true,
                isLoadingChapter = true,
                isChapterSettleComplete = true,
                hasReaderUserInteracted = true,
            )
        )
    }

    @Test
    fun shouldPrefetchAdjacentReaderChapters_skipsColdOpenUntilReaderHasBeenTouched() {
        assertFalse(
            shouldPrefetchAdjacentReaderChapters(
                currentChapterIndex = 2,
                spineSize = 5,
                hasChapterElements = true,
                isLoadingChapter = false,
                isChapterSettleComplete = true,
                hasReaderUserInteracted = false,
            )
        )
    }

    @Test
    fun shouldPrefetchAdjacentReaderChapters_runsOnceReaderHasBeenTouched() {
        assertTrue(
            shouldPrefetchAdjacentReaderChapters(
                currentChapterIndex = 2,
                spineSize = 5,
                hasChapterElements = true,
                isLoadingChapter = false,
                isChapterSettleComplete = true,
                hasReaderUserInteracted = true,
            )
        )
    }
}
