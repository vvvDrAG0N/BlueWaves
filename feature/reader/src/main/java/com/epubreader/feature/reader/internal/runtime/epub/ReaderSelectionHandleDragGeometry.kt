package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

@Immutable
internal data class ReaderSelectionHandleDragGeometry(
    val textAnchorInHost: Offset,
    val visualPickupPointInHost: Offset,
) {
    val textAnchorOffsetFromVisualPickup: Offset
        get() = textAnchorInHost - visualPickupPointInHost

    fun resolveLogicalTextAnchorPointer(
        fingerPointerInHost: Offset,
        fingerOffsetFromVisualPickup: Offset,
    ): Offset {
        val draggedVisualPickupPoint = fingerPointerInHost - fingerOffsetFromVisualPickup
        return draggedVisualPickupPoint + textAnchorOffsetFromVisualPickup
    }
}

internal fun resolveReaderSelectionHandleDragGeometry(
    textAnchorInHost: Offset,
    handleTopLeft: Offset,
    visualPickupPointInHandle: Offset,
): ReaderSelectionHandleDragGeometry {
    return ReaderSelectionHandleDragGeometry(
        textAnchorInHost = textAnchorInHost,
        visualPickupPointInHost = Offset(
            x = handleTopLeft.x + visualPickupPointInHandle.x,
            y = handleTopLeft.y + visualPickupPointInHandle.y,
        ),
    )
}
