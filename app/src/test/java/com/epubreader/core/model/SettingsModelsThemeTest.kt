package com.epubreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsModelsThemeTest {

    @Test
    fun availableThemeOptions_keepsBuiltInsAheadOfCustomThemes() {
        val customTheme = customTheme(id = "custom-ocean", name = "Ocean")

        val options = availableThemeOptions(listOf(customTheme))

        assertEquals(
            listOf(LightThemeId, SepiaThemeId, DarkThemeId, OledThemeId, "custom-ocean"),
            options.map { it.id },
        )
    }

    @Test
    fun normalizeThemeSelection_acceptsBuiltInAndCustomIds() {
        val customTheme = customTheme(id = "custom-ocean", name = "Ocean")

        assertEquals(DarkThemeId, normalizeThemeSelection(DarkThemeId, emptyList()))
        assertEquals("custom-ocean", normalizeThemeSelection("custom-ocean", listOf(customTheme)))
        assertEquals(LightThemeId, normalizeThemeSelection("missing", listOf(customTheme)))
    }

    @Test
    fun parseAndFormatThemeColor_supportRgbAndArgbHex() {
        assertEquals(0xFFF4ECD8, parseThemeColorOrNull("#F4ECD8"))
        assertEquals(0xCC102030, parseThemeColorOrNull("CC102030"))
        assertNull(parseThemeColorOrNull("#12345"))
        assertEquals("#102030", formatThemeColor(0xCC102030))
    }

    private fun customTheme(id: String, name: String): CustomTheme {
        return CustomTheme(
            id = id,
            name = name,
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
    }
}
