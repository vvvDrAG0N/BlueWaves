package com.epubreader.feature.reader.internal

import com.epubreader.core.debug.AppLog

// Flip this on temporarily when diagnosing reader selection transitions on a debug build.
private const val ReaderSelectionTransitionLoggingEnabled = false

internal fun logReaderSelectionTransition(message: () -> String) {
    if (ReaderSelectionTransitionLoggingEnabled) {
        AppLog.d(AppLog.READER, message)
    }
}
