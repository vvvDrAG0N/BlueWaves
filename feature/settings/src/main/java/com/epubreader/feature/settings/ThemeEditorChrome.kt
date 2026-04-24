package com.epubreader.feature.settings

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.ThemePalette
import com.epubreader.core.model.themePaletteSeed

internal fun themeEditorChromePalette(
    themeId: String,
    customThemes: List<CustomTheme>,
): ThemePalette {
    return themePaletteSeed(themeId, customThemes)
}

internal fun themeEditorColorScheme(
    themeId: String,
    customThemes: List<CustomTheme>,
): ColorScheme {
    return themeEditorColorSchemeFromPalette(themeEditorChromePalette(themeId, customThemes))
}

private fun themeEditorColorSchemeFromPalette(palette: ThemePalette): ColorScheme {
    val primary = Color(palette.accent)
    val secondary = Color(palette.chromeAccent)
    val background = Color(palette.appBackground)
    val surface = Color(palette.appSurface)
    val surfaceVariant = Color(palette.appSurfaceVariant)
    val outline = Color(palette.appOutline)
    val onSurface = Color(palette.appForeground)
    val onSurfaceVariant = Color(palette.appForegroundMuted)
    val isDarkPalette = averageLuminance(
        background = background,
        surface = surface,
        readerBackground = Color(palette.readerBackground),
    ) < 0.45f
    val tertiary = lerp(primary, secondary, 0.45f)
    val primaryContainer = themedContainer(surface, primary, isDarkPalette)
    val secondaryContainer = themedContainer(surface, secondary, isDarkPalette)
    val tertiaryContainer = themedContainer(surface, tertiary, isDarkPalette)

    val base = if (isDarkPalette) {
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
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
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
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = lerp(surfaceVariant, outline, 0.35f),
        )
    }

    return base.copy(
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
