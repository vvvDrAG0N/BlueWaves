package com.epubreader.core.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable

inline fun containSurfaceExtensionFailure(
    pointId: String,
    extensionId: String,
    onFailure: (ExtensionFailure) -> Unit,
    render: () -> Unit,
): Boolean {
    return runCatching(render)
        .onFailure { cause ->
            onFailure(
                ExtensionFailure(
                    pointId = pointId,
                    extensionId = extensionId,
                    cause = cause,
                ),
            )
        }
        .isSuccess
}

@Composable
inline fun RenderSurfaceExtension(
    pointId: String,
    extensionId: String,
    noinline onFailure: (ExtensionFailure) -> Unit,
    noinline content: @Composable () -> Unit,
) {
    containSurfaceExtensionFailure(
        pointId = pointId,
        extensionId = extensionId,
        onFailure = onFailure,
    ) {
        content()
    }
}

@Composable
inline fun BoxScope.RenderSurfaceBoxExtension(
    pointId: String,
    extensionId: String,
    noinline onFailure: (ExtensionFailure) -> Unit,
    noinline content: @Composable BoxScope.() -> Unit,
) {
    containSurfaceExtensionFailure(
        pointId = pointId,
        extensionId = extensionId,
        onFailure = onFailure,
    ) {
        content()
    }
}
