package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.ui.text.TextRange

internal fun resolveReaderSelectAllRange(totalTextLength: Int): TextRange? {
    if (totalTextLength <= 0) {
        return null
    }

    return TextRange(0, totalTextLength)
}
