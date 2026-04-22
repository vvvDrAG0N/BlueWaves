package com.epubreader.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.themePaletteSeed

val KarlaFont = FontFamily(
    Font(R.font.karla, FontWeight.Normal)
)

data class ReaderTheme(
    val background: Color, 
    val foreground: Color,
    val variantForeground: Color,
    val primary: Color,
)

typealias GlobalSettingsTransform = (GlobalSettings) -> GlobalSettings

fun getThemeColors(
    theme: String,
    customThemes: List<CustomTheme> = emptyList(),
): ReaderTheme {
    val palette = themePaletteSeed(theme, customThemes)
    val background = Color(palette.readerBackground)
    val foreground = Color(palette.readerForeground)
    val primary = Color(palette.primary)
    
    // Derive secondary foreground color (onSurfaceVariant equivalent)
    // We use a 70% alpha of the foreground to ensure tonal harmony
    val variantForeground = foreground.copy(alpha = 0.7f)
    
    return ReaderTheme(
        background = background,
        foreground = foreground,
        variantForeground = variantForeground,
        primary = primary,
    )
}
