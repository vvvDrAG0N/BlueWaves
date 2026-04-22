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

data class ReaderTheme(val background: Color, val foreground: Color)

typealias GlobalSettingsTransform = (GlobalSettings) -> GlobalSettings

fun getThemeColors(
    theme: String,
    customThemes: List<CustomTheme> = emptyList(),
): ReaderTheme {
    val palette = themePaletteSeed(theme, customThemes)
    return ReaderTheme(
        background = Color(palette.readerBackground),
        foreground = Color(palette.readerForeground),
    )
}
