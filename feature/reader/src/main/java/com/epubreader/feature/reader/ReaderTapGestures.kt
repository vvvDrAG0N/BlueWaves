package com.epubreader.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

internal fun Modifier.readerTapGesture(
    onTap: () -> Unit,
): Modifier = pointerInput(onTap) {
    val touchSlop = viewConfiguration.touchSlop
    val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis.toLong()

    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Final,
        )
        val pointerId = down.id
        val startPosition = down.position
        val startUptimeMillis = down.uptimeMillis

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            val distanceFromStart = (change.position - startPosition).readerDistance()
            val movedPastTouchSlop = distanceFromStart > touchSlop
            val exceededLongPressTimeout =
                change.uptimeMillis - startUptimeMillis >= longPressTimeoutMillis

            if (change.changedToUpIgnoreConsumed()) {
                if (!change.isConsumed && !movedPastTouchSlop && !exceededLongPressTimeout) {
                    onTap()
                }
                break
            }

            if (!change.pressed || change.isConsumed || movedPastTouchSlop || exceededLongPressTimeout) {
                break
            }
        }
    }
}

private fun Offset.readerDistance(): Float {
    if (!isSpecified) {
        return Float.POSITIVE_INFINITY
    }
    return hypot(x.toDouble(), y.toDouble()).toFloat()
}
