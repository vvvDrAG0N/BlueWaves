package com.epubreader.feature.reader

import androidx.compose.ui.graphics.Color
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
}
