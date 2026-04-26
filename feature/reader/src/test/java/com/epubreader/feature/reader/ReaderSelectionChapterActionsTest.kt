package com.epubreader.feature.reader

import androidx.compose.ui.text.TextRange
import com.epubreader.feature.reader.internal.runtime.epub.resolveReaderSelectAllRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSelectionChapterActionsTest {

    @Test
    fun resolveReaderSelectAllRange_returnsNullForEmptyOrNonPositiveLengths() {
        assertNull(resolveReaderSelectAllRange(totalTextLength = 0))
        assertNull(resolveReaderSelectAllRange(totalTextLength = -1))
    }

    @Test
    fun resolveReaderSelectAllRange_returnsFullDocumentRangeForNonEmptyLengths() {
        assertEquals(
            TextRange(0, 7),
            resolveReaderSelectAllRange(totalTextLength = 7),
        )
    }
}
