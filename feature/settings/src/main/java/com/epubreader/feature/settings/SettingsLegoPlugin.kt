package com.epubreader.feature.settings

import androidx.compose.runtime.Composable
import com.epubreader.core.ui.FeatureLegoPlugin
import com.epubreader.data.settings.SettingsManager

object SettingsRoute

data class SettingsDependencies(
    val settingsManager: SettingsManager,
)

sealed interface SettingsEvent {
    data object Back : SettingsEvent
}

object SettingsLegoPlugin : FeatureLegoPlugin<SettingsRoute, SettingsDependencies, SettingsEvent> {
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
