package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

internal fun Modifier.readerSelectionLongPressGesture(
    onLongPressStart: (Offset) -> Unit,
    onLongPressDrag: (Offset) -> Unit = {},
    onLongPressEnd: () -> Unit = {},
): Modifier = composed {
    val latestOnLongPressStart by rememberUpdatedState(onLongPressStart)
    val latestOnLongPressDrag by rememberUpdatedState(onLongPressDrag)
    val latestOnLongPressEnd by rememberUpdatedState(onLongPressEnd)

    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Final,
            )
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress != null) {
                longPress.consume()
                latestOnLongPressStart(longPress.position)
                var activeChange: PointerInputChange = longPress
                try {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == activeChange.id } ?: break
                        if (change.positionChanged()) {
                            change.consume()
                            latestOnLongPressDrag(change.position)
                        }
                        if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                            change.consume()
                            break
                        }
                        activeChange = change
                    }
                } finally {
                    latestOnLongPressEnd()
                }
            }
        }
    }
}

internal suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectReaderHandleDragGestures(
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var pointerChange: PointerInputChange = down
        down.consume()
        onDragStart(down.position)

        try {
            while (true) {
                val event = awaitPointerEvent()
                val activeChange = event.changes.firstOrNull { it.id == pointerChange.id } ?: break

                if (activeChange.positionChanged()) {
                    activeChange.consume()
                    onDrag(activeChange.position)
                }

                if (activeChange.changedToUpIgnoreConsumed() || !activeChange.pressed) {
                    activeChange.consume()
                    break
                }

                pointerChange = activeChange
            }
        } finally {
            onDragEnd()
        }
    }
}
