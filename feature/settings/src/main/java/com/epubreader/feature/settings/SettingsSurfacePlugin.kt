package com.epubreader.feature.settings

import androidx.compose.runtime.Composable
import com.epubreader.core.ui.SurfaceId
import com.epubreader.core.ui.SurfacePlugin
import com.epubreader.core.ui.SurfaceRouteDecodeResult
import com.epubreader.data.settings.SettingsManager

object SettingsRoute

data class SettingsDependencies(
    val settingsManager: SettingsManager,
)

sealed interface SettingsEvent {
    data object Back : SettingsEvent
}

object SettingsSurfacePlugin : SurfacePlugin<SettingsRoute, SettingsDependencies, SettingsEvent> {
    override val surfaceId: SurfaceId = SurfaceId("settings")

    override fun decodeRouteArgs(routeArgs: Any?): SurfaceRouteDecodeResult<SettingsRoute> {
        return if (routeArgs == null) {
            SurfaceRouteDecodeResult.Success(SettingsRoute)
        } else {
            SurfaceRouteDecodeResult.Failure("Settings does not accept route arguments.")
        }
    }

    @Composable
    override fun Render(
        route: SettingsRoute,
        dependencies: SettingsDependencies,
        onEvent: (SettingsEvent) -> Unit,
    ) {
        SettingsScreen(
            settingsManager = dependencies.settingsManager,
            onBack = { onEvent(SettingsEvent.Back) },
        )
    }
}
