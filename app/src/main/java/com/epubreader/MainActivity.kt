package com.epubreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.epubreader.app.AppNavigation
import com.epubreader.data.settings.SettingsManager

/**
 * Entry point of the application.
 * Keeps app bootstrap concerns separate from the navigation and feature UI code.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsManager = SettingsManager(this)

        setContent {
            val globalSettingsState = settingsManager.globalSettings.collectAsState(initial = null)
            val globalSettings = globalSettingsState.value

            if (globalSettings != null) {
                MaterialTheme(colorScheme = appColorScheme(globalSettings.theme)) {
                    AppNavigation(settingsManager, globalSettings)
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
    }
}

enum class Screen {
    Library,
    Reader,
    Settings
}

private fun appColorScheme(theme: String): ColorScheme {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFFD0BCFF),
        secondary = Color(0xFFCCC2DC),
        tertiary = Color(0xFFEFB8C8)
    )

    val oledColorScheme = darkColorScheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color.White,
        surfaceContainer = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
        outline = Color.White.copy(alpha = 0.2f),
        outlineVariant = Color.White.copy(alpha = 0.1f)
    )

    return when (theme) {
        "oled" -> oledColorScheme
        "dark" -> darkColorScheme
        else -> lightColorScheme()
    }
}
