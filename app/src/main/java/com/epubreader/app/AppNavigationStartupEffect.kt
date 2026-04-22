package com.epubreader.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.epubreader.Screen
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
internal fun AppNavigationStartupEffect(
    context: Context,
    currentScreen: Screen,
    globalSettings: GlobalSettings,
    settingsManager: SettingsManager,
    onDetectedVersionCode: (Int) -> Unit,
    onShowFirstTimeNoteChange: (Boolean) -> Unit,
    onChangelogChange: (List<JSONObject>) -> Unit,
    onRefreshLibrary: () -> Unit,
) {
    LaunchedEffect(Unit) {
        // Extended yield to the UI thread for initial rendering and JIT warm-up.
        // This ensures the theme carousel has a clear window to reach 60fps.
        delay(500)

        val startupDecision = evaluateAppShellStartup(context, globalSettings)
        onDetectedVersionCode(startupDecision.detectedVersionCode)
        onShowFirstTimeNoteChange(startupDecision.showFirstTimeNote)
        onChangelogChange(startupDecision.changelogEntries)

        if (startupDecision.shouldClearFirstTime) {
            settingsManager.setFirstTime(false)
        }
        startupDecision.versionCodeToMarkSeen?.let { settingsManager.setLastSeenVersionCode(it) }

        if (currentScreen == Screen.Library) {
            onRefreshLibrary()
        }
    }
}
