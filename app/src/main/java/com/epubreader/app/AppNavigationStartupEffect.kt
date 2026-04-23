package com.epubreader.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.settings.SettingsManager

@Composable
internal fun AppNavigationStartupEffect(
    context: Context,
    globalSettings: GlobalSettings,
    settingsManager: SettingsManager,
    onStartupEvaluated: (AppShellStartupDecision) -> Unit,
) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        val startupDecision = evaluateAppShellStartup(context, globalSettings)
        onStartupEvaluated(startupDecision)

        if (startupDecision.shouldClearFirstTime) {
            settingsManager.setFirstTime(false)
        }
        startupDecision.versionCodeToMarkSeen?.let { settingsManager.setLastSeenVersionCode(it) }
    }
}
