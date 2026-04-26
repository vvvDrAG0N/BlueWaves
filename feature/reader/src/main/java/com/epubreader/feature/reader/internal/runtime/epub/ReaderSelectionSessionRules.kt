package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.ui.text.TextRange

internal fun shouldClearReaderStaleSelection(
    normalizedSelection: TextRange?,
    selectedText: String,
    isHandleDragActive: Boolean,
): Boolean {
    return normalizedSelection?.collapsed == true && !isHandleDragActive
}
