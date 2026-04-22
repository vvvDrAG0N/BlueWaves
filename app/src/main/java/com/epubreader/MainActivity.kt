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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.epubreader.app.AppNavigation
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.ThemePalette
import com.epubreader.core.model.themePaletteSeed
import com.epubreader.data.settings.SettingsManager
import com.epubreader.feature.settings.ThemeStudioScreen

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
                MaterialTheme(
                    colorScheme = appColorScheme(
                        theme = globalSettings.theme,
                        customThemes = globalSettings.customThemes,
                    )
                ) {
                    val defaultHaptics = LocalHapticFeedback.current
                    val haptics = if (globalSettings.hapticFeedback) {
                        defaultHaptics
                    } else {
                        object : HapticFeedback {
                            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
                        }
                    }

                    // Disable view-level haptics for the root window when the setting is off.
                    // This catches system gestures like back-swipes that the OS triggers.
                    LaunchedEffect(globalSettings.hapticFeedback) {
                        window.decorView.isHapticFeedbackEnabled = globalSettings.hapticFeedback
                    }

                    CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                        AppNavigation(
                            settingsManager = settingsManager,
                            globalSettings = globalSettings
                        )
                    }
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
    Settings,
    EditBook,
}

internal fun appColorScheme(
    theme: String,
    customThemes: List<CustomTheme> = emptyList(),
): ColorScheme {
    return colorSchemeFromPalette(themePaletteSeed(theme, customThemes))
}

private fun customAppColorScheme(theme: CustomTheme): ColorScheme {
    return colorSchemeFromPalette(theme.palette)
}

private fun colorSchemeFromPalette(palette: ThemePalette): ColorScheme {
    val primary = Color(palette.primary)
    val secondary = Color(palette.secondary)
    val background = Color(palette.background)
    val surface = Color(palette.surface)
    val surfaceVariant = Color(palette.surfaceVariant)
    val outline = Color(palette.outline)
    val systemForeground = Color(palette.systemForeground)
    val isDarkPalette = averageLuminance(
        background = background,
        surface = surface,
        readerBackground = Color(palette.readerBackground),
    ) < 0.45f
    val tertiary = lerp(primary, secondary, 0.45f)
    val primaryContainer = themedContainer(surface, primary, isDarkPalette)
    val secondaryContainer = themedContainer(surface, secondary, isDarkPalette)
    val tertiaryContainer = themedContainer(surface, tertiary, isDarkPalette)

    val scheme = if (isDarkPalette) {
        darkColorScheme(
            primary = primary,
            onPrimary = contentColorFor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = contentColorFor(primaryContainer),
            secondary = secondary,
            onSecondary = contentColorFor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = contentColorFor(secondaryContainer),
            tertiary = tertiary,
            onTertiary = contentColorFor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = contentColorFor(tertiaryContainer),
            background = background,
            onBackground = systemForeground,
            surface = surface,
            onSurface = systemForeground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = systemForeground,
            outline = outline,
            outlineVariant = lerp(surfaceVariant, outline, 0.35f),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = contentColorFor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = contentColorFor(primaryContainer),
            secondary = secondary,
            onSecondary = contentColorFor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = contentColorFor(secondaryContainer),
            tertiary = tertiary,
            onTertiary = contentColorFor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = contentColorFor(tertiaryContainer),
            background = background,
            onBackground = systemForeground,
            surface = surface,
            onSurface = systemForeground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = systemForeground,
            outline = outline,
            outlineVariant = lerp(surfaceVariant, outline, 0.35f),
        )
    }

    return scheme.copy(
        surfaceContainerLowest = lerp(surface, background, if (isDarkPalette) 0.05f else 0.08f),
        surfaceContainerLow = lerp(surface, surfaceVariant, if (isDarkPalette) 0.12f else 0.08f),
        surfaceContainer = lerp(surface, surfaceVariant, if (isDarkPalette) 0.2f else 0.15f),
        surfaceContainerHigh = lerp(surface, surfaceVariant, if (isDarkPalette) 0.3f else 0.24f),
        surfaceContainerHighest = lerp(surface, surfaceVariant, if (isDarkPalette) 0.4f else 0.34f),
    )
}

private fun contentColorFor(color: Color): Color {
    return if (color.luminance() > 0.5f) Color.Black else Color.White
}

private fun themedContainer(base: Color, accent: Color, isDarkPalette: Boolean): Color {
    return lerp(base, accent, if (isDarkPalette) 0.28f else 0.18f)
}

private fun averageLuminance(
    background: Color,
    surface: Color,
    readerBackground: Color,
): Float {
    return (background.luminance() + surface.luminance() + readerBackground.luminance()) / 3f
}
