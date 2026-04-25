package com.epubreader.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

@Immutable
data class ShellChromeSpec(
    val immersiveSystemBars: Boolean = false,
)

interface FeatureLegoPlugin<Route, Dependencies, Event> {
    val chromeSpec: ShellChromeSpec
        get() = ShellChromeSpec()

    @Composable
    fun Render(
        route: Route,
        dependencies: Dependencies,
        onEvent: (Event) -> Unit,
    )
}
