package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

internal fun resolveVisibleReaderSelectionHandleAnchor(
    anchorInHost: Offset?,
    hostSize: IntSize,
): Offset? {
    val resolvedAnchor = anchorInHost ?: return null
    if (hostSize.width <= 0 || hostSize.height <= 0) {
        return resolvedAnchor
    }
    val hostWidth = hostSize.width.toFloat()
    val hostHeight = hostSize.height.toFloat()
    return resolvedAnchor.takeIf { anchor ->
        anchor.x in 0f..hostWidth && anchor.y in 0f..hostHeight
    }
}

internal fun shouldMoveReaderSelectionActionBarToTop(
    actionBarReferenceY: Float?,
    hostHeight: Int,
    actionBarCollisionZonePx: Int,
): Boolean {
    return actionBarReferenceY?.let { anchorY ->
        hostHeight > 0 && anchorY >= (hostHeight - actionBarCollisionZonePx)
    } == true
}
