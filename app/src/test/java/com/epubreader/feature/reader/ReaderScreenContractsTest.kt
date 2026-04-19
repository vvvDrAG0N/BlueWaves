package com.epubreader.feature.reader

import androidx.compose.ui.graphics.Color
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderScreenContractsTest {

    @Test
    fun getThemeColors_returnsLightThemeForUnknownTheme() {
        val theme = getThemeColors("custom")

        assertEquals(Color.White, theme.background)
        assertEquals(Color.Black, theme.foreground)
    }

    @Test
    fun getThemeColors_returnsExpectedLightTheme() {
        val theme = getThemeColors("light")

        assertEquals(Color.White, theme.background)
        assertEquals(Color.Black, theme.foreground)
    }

    @Test
    fun getThemeColors_returnsExpectedOledTheme() {
        val theme = getThemeColors("oled")

        assertEquals(Color.Black, theme.background)
        assertEquals(Color.White, theme.foreground)
    }

    @Test
    fun getThemeColors_returnsExpectedDarkTheme() {
        val theme = getThemeColors("dark")

        assertEquals(Color(0xFF121212), theme.background)
        assertEquals(Color.White, theme.foreground)
    }

    @Test
    fun getThemeColors_returnsExpectedSepiaTheme() {
        val theme = getThemeColors("sepia")

        assertEquals(Color(0xFFF4ECD8), theme.background)
        assertEquals(Color(0xFF5B4636), theme.foreground)
    }

    @Test
    fun getThemeColors_returnsCustomThemeWhenAvailable() {
        val theme = getThemeColors(
            theme = "custom-ocean",
            customThemes = listOf(
                CustomTheme(
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
                        systemForeground = 0xFF000000,
                    ),
                )
            ),
        )

        assertEquals(Color(0xFFEEF8FF), theme.background)
        assertEquals(Color(0xFF10212D), theme.foreground)
    }

    @Test
    fun resolveReaderBackAction_closesTopmostReaderOverlayOneLayerAtATime() {
        assertEquals(
            ReaderBackAction.CloseToc,
            resolveReaderBackAction(
                isDrawerOpen = true,
                isTextSelectionSessionActive = true,
                showControls = true,
            ),
        )

        assertEquals(
            ReaderBackAction.ClearTextSelection,
            resolveReaderBackAction(
                isDrawerOpen = false,
                isTextSelectionSessionActive = true,
                showControls = true,
            ),
        )

        assertEquals(
            ReaderBackAction.HideControls,
            resolveReaderBackAction(
                isDrawerOpen = false,
                isTextSelectionSessionActive = false,
                showControls = true,
            ),
        )

        assertEquals(
            ReaderBackAction.ExitReader,
            resolveReaderBackAction(
                isDrawerOpen = false,
                isTextSelectionSessionActive = false,
                showControls = false,
            ),
        )
    }
}
