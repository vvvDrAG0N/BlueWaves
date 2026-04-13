package com.epubreader

import androidx.compose.ui.graphics.Color
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityThemeTest {

    @Test
    fun appColorScheme_returnsSepiaPaletteForSepiaTheme() {
        val colorScheme = appColorScheme("sepia")

        assertEquals(Color(0xFFF4ECD8), colorScheme.background)
        assertEquals(Color(0xFF5B4636), colorScheme.onBackground)
        assertEquals(Color(0xFFEADDC6), colorScheme.surfaceContainerHigh)
    }

    @Test
    fun appColorScheme_usesCustomThemePaletteForCustomTheme() {
        val customTheme = CustomTheme(
            id = "custom-ocean",
            name = "Ocean",
            palette = ThemePalette(
                primary = 0xFF2A6F97,
                secondary = 0xFF468FAF,
                background = 0xFFF4FAFF,
                surface = 0xFFFFFFFF,
                surfaceVariant = 0xFFD7EAF7,
                outline = 0xFF8AA7BB,
                readerBackground = 0xFFEEF8FF,
                readerForeground = 0xFF10212D,
            ),
        )

        val colorScheme = appColorScheme(
            theme = customTheme.id,
            customThemes = listOf(customTheme),
        )

        assertEquals(Color(0xFF2A6F97), colorScheme.primary)
        assertEquals(Color(0xFFF4FAFF), colorScheme.background)
        assertEquals(Color(0xFFFFFFFF), colorScheme.surface)
    }
}
