package com.epubreader.feature.reader

import androidx.compose.ui.text.TextRange
import com.epubreader.feature.reader.internal.runtime.epub.shouldClearReaderStaleSelection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionSessionRulesTest {

    @Test
    fun shouldClearReaderStaleSelection_returnsFalseWhileAHandleDragKeepsAWhitespaceOnlySelectionAlive() {
        assertFalse(
            shouldClearReaderStaleSelection(
                normalizedSelection = TextRange(5, 6),
                selectedText = "",
                isHandleDragActive = true,
            ),
        )
    }

    @Test
    fun shouldClearReaderStaleSelection_keepsWhitespaceOnlySelectionsAfterRelease() {
        assertFalse(
            shouldClearReaderStaleSelection(
                normalizedSelection = TextRange(5, 6),
                selectedText = "",
                isHandleDragActive = false,
            ),
        )
    }

    @Test
    fun shouldClearReaderStaleSelection_returnsFalseForANonBlankSelection() {
        assertFalse(
            shouldClearReaderStaleSelection(
                normalizedSelection = TextRange(0, 5),
                selectedText = "Alpha",
                isHandleDragActive = false,
            ),
        )
    }
}
