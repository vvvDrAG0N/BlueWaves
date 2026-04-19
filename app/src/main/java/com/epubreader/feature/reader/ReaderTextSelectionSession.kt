package com.epubreader.feature.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun interface ReaderTextSelectionCancellable {
    fun cancel()
}

internal fun interface ReaderTextSelectionScheduler {
    fun schedule(
        delayMillis: Long,
        action: () -> Unit,
    ): ReaderTextSelectionCancellable
}

internal class ReaderTextSelectionSession(
    private val scheduler: ReaderTextSelectionScheduler,
    private val clearDelayMillis: Long = 200L,
    private val onActiveChanged: (Boolean) -> Unit,
) {
    var isActive by mutableStateOf(false)
        private set

    var copyAction: (() -> Unit)? = null
        private set

    private var isPointerDown = false
    private var deferHideUntilPointerRelease = false
    private var pendingClear: ReaderTextSelectionCancellable? = null

    fun showMenu(onCopyRequested: (() -> Unit)?) {
        pendingClear?.cancel()
        pendingClear = null
        deferHideUntilPointerRelease = false
        copyAction = onCopyRequested
        updateActive(true)
    }

    fun hide() {
        if (!isActive && copyAction == null) {
            return
        }
        if (isPointerDown) {
            deferHideUntilPointerRelease = true
            return
        }
        scheduleClear()
    }

    fun onPointerPressed() {
        isPointerDown = true
    }

    fun onPointerReleased() {
        isPointerDown = false
        if (deferHideUntilPointerRelease) {
            deferHideUntilPointerRelease = false
            scheduleClear()
        }
    }

    fun reset() {
        pendingClear?.cancel()
        pendingClear = null
        isPointerDown = false
        deferHideUntilPointerRelease = false
        clearSelection()
    }

    private fun scheduleClear() {
        pendingClear?.cancel()
        pendingClear = scheduler.schedule(clearDelayMillis) {
            clearSelection()
        }
    }

    private fun clearSelection() {
        pendingClear = null
        copyAction = null
        updateActive(false)
    }

    private fun updateActive(active: Boolean) {
        if (isActive == active) {
            return
        }
        isActive = active
        onActiveChanged(active)
    }
}

internal fun createReaderTextSelectionScheduler(
    scope: CoroutineScope,
): ReaderTextSelectionScheduler = ReaderTextSelectionScheduler { delayMillis, action ->
    val job = scope.launch {
        delay(delayMillis)
        action()
    }
    ReaderTextSelectionCancellable {
        job.cancel()
    }
}
