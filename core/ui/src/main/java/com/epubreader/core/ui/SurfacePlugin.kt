package com.epubreader.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

@JvmInline
@Immutable
value class SurfaceId(val value: String) {
    init {
        require(value.isNotBlank()) { "SurfaceId cannot be blank." }
    }

    override fun toString(): String = value
}

interface SurfaceRouteDefinition {
    val surfaceId: SurfaceId
    val chromeSpec: ShellChromeSpec
        get() = ShellChromeSpec()
}

sealed interface SurfaceRouteDecodeResult<out Args> {
    data class Success<Args>(val args: Args) : SurfaceRouteDecodeResult<Args>
    data class Failure(val message: String) : SurfaceRouteDecodeResult<Nothing>
}

interface SurfacePlugin<Args, Dependencies, Event> : SurfaceRouteDefinition {
    fun decodeRouteArgs(routeArgs: Any?): SurfaceRouteDecodeResult<Args>

    @Composable
    fun Render(
        route: Args,
        dependencies: Dependencies,
        onEvent: (Event) -> Unit,
    )
}
