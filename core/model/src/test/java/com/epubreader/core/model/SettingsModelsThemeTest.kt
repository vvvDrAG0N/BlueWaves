package com.epubreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsModelsThemeTest {

    @Test
    fun availableThemeOptions_keepsBuiltInsAheadOfCustomThemes() {
        val customTheme = customTheme(id = "custom-ocean", name = "Ocean")

        val options = availableThemeOptions(listOf(customTheme))

        assertEquals(
            listOf(LightThemeId, SepiaThemeId, DarkThemeId, OledThemeId, ForestThemeId, "custom-ocean"),
            options.map { it.id },
        )
    }

    @Test
    fun normalizeThemeSelection_acceptsBuiltInAndCustomIds() {
        val customTheme = customTheme(id = "custom-ocean", name = "Ocean")

        assertEquals(DarkThemeId, normalizeThemeSelection(DarkThemeId, emptyList()))
        assertEquals(SepiaThemeId, normalizeThemeSelection(AzureThemeId, emptyList()))
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

    @Test
    fun generatePaletteFromBase_populatesExpandedRolesWithReadableContrast() {
        val palette = generatePaletteFromBase(
            primary = 0xFF2A6F97,
            background = 0xFFF4FAFF,
        )

        assertTrue(colorContrast(palette.systemForeground, palette.background) >= 4.5)
        assertTrue(colorContrast(palette.appForegroundMuted, palette.background) >= 3.0)
        assertTrue(colorContrast(palette.readerForeground, palette.readerBackground) >= 7.0)
        assertTrue(colorContrast(palette.readerAccent, palette.readerBackground) >= 4.5)
        assertTrue(palette.overlayScrim != palette.background)
        assertTrue(colorContrast(palette.startupForeground, palette.startupBackground) >= 7.0)
        assertTrue(colorContrast(palette.favoriteAccent, palette.background) >= 3.0)
        assertTrue(colorContrast(palette.favoriteAccent, palette.surface) >= 3.0)
        assertTrue(colorContrast(palette.coverOverlayForeground, palette.coverOverlayScrim) >= 4.5)
    }

    @Test
    fun legacyThemePalette_defaultsExpandToStartupFavoriteAndCoverOverlayRoles() {
        val palette = ThemePalette(
            primary = 0xFF2A6F97,
            secondary = 0xFF468FAF,
            background = 0xFFF4FAFF,
            surface = 0xFFFFFFFF,
            surfaceVariant = 0xFFD7EAF7,
            outline = 0xFF8AA7BB,
            readerBackground = 0xFFEEF8FF,
            readerForeground = 0xFF10212D,
            systemForeground = 0xFF000000,
        )

        assertTrue(colorContrast(palette.startupForeground, palette.startupBackground) >= 7.0)
        assertTrue(colorContrast(palette.favoriteAccent, palette.background) >= 3.0)
        assertTrue(colorContrast(palette.coverOverlayForeground, palette.coverOverlayScrim) >= 4.5)
    }

    @Test
    fun generatePaletteFromGuidedInput_keepsReaderIndependentWhenUnlinked() {
        val palette = generatePaletteFromGuidedInput(
            GuidedThemePaletteInput(
                accent = 0xFF7C3AED,
                appBackground = 0xFF111827,
                readerBackground = 0xFFF4ECD8,
                readerForeground = 0xFF5B4636,
                readerLinked = false,
            ),
        )

        assertEquals(0xFFF4ECD8, palette.readerBackground)
        assertEquals(0xFF5B4636, palette.readerForeground)
        assertTrue(colorContrast(palette.readerForeground, palette.readerBackground) >= 7.0)
    }

    @Test
    fun generatePaletteFromGuidedInput_prefersProvidedSemanticOverridesWhenPresent() {
        val palette = generatePaletteFromGuidedInput(
            GuidedThemePaletteInput(
                accent = 0xFF2563EB,
                appBackground = 0xFFF8FAFC,
                chromeAccent = 0xFF155E75,
                appSurface = 0xFFFFFFFF,
                appForeground = 0xFF0F172A,
                appForegroundMuted = 0xFF334155,
                overlayScrim = 0xFF1E293B,
            ),
        )

        assertEquals(0xFF2563EB, palette.accent)
        assertEquals(0xFF155E75, palette.chromeAccent)
        assertEquals(0xFFFFFFFF, palette.appSurface)
        assertEquals(0xFF0F172A, palette.appForeground)
        assertEquals(0xFF334155, palette.appForegroundMuted)
        assertEquals(0xFF1E293B, palette.overlayScrim)
    }

    @Test
    fun isPaletteReaderLinked_detectsLinkedAndSplitReaderPalettes() {
        val linkedPalette = generatePaletteFromBase(
            primary = 0xFF4F46E5,
            background = 0xFFFAF9F6,
        )
        val splitPalette = linkedPalette.copy(
            readerBackground = 0xFFF4ECD8,
            readerForeground = 0xFF5B4636,
            readerForegroundMuted = 0xFF8C7767,
            readerAccent = 0xFF8B5E3C,
        )

        assertTrue(isPaletteReaderLinked(linkedPalette))
        assertFalse(isPaletteReaderLinked(splitPalette))
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

    private fun colorContrast(foreground: Long, background: Long): Double {
        val foregroundLuminance = calculateLuminance(foreground)
        val backgroundLuminance = calculateLuminance(background)
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return ((lighter + 0.05f) / (darker + 0.05f)).toDouble()
    }
}
