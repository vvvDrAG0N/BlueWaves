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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.epubreader.app.AppNavigation
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.DarkThemeId
import com.epubreader.core.model.LightThemeId
import com.epubreader.core.model.OledThemeId
import com.epubreader.core.model.SepiaThemeId
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
                MaterialTheme(
                    colorScheme = appColorScheme(
                        theme = globalSettings.theme,
                        customThemes = globalSettings.customThemes,
                    )
                ) {
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
    Settings,
    EditBook,
}

internal fun appColorScheme(
    theme: String,
    customThemes: List<CustomTheme> = emptyList(),
): ColorScheme {
    customThemes.firstOrNull { it.id == theme }?.let(::customAppColorScheme)?.let { return it }

    val darkColorScheme = darkColorScheme(
        primary = Color(0xFFD0BCFF),
        secondary = Color(0xFFCCC2DC),
        tertiary = Color(0xFFEFB8C8)
    )

    val sepiaColorScheme = lightColorScheme(
        primary = Color(0xFF8A5A44),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6D3BF),
        onPrimaryContainer = Color(0xFF3F2C1F),
        secondary = Color(0xFF7A6657),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4D8CB),
        onSecondaryContainer = Color(0xFF35291F),
        tertiary = Color(0xFF8C6F56),
        onTertiary = Color.White,
        background = Color(0xFFF4ECD8),
        onBackground = Color(0xFF5B4636),
        surface = Color(0xFFF4ECD8),
        onSurface = Color(0xFF5B4636),
        surfaceVariant = Color(0xFFE8DCC9),
        onSurfaceVariant = Color(0xFF6A5849),
        outline = Color(0xFF8F7C6C),
        outlineVariant = Color(0xFFCDBCA9),
    ).copy(
        surfaceContainerLowest = Color(0xFFFFF8EE),
        surfaceContainerLow = Color(0xFFF9F1E0),
        surfaceContainer = Color(0xFFF2E7D2),
        surfaceContainerHigh = Color(0xFFEADDC6),
        surfaceContainerHighest = Color(0xFFE3D3BB),
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
        OledThemeId -> oledColorScheme
        DarkThemeId -> darkColorScheme
        SepiaThemeId -> sepiaColorScheme
        LightThemeId -> lightColorScheme()
        else -> lightColorScheme()
    }
}

private fun customAppColorScheme(theme: CustomTheme): ColorScheme {
    val palette = theme.palette
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
