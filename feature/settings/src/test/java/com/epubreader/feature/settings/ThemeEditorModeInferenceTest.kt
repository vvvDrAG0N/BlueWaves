package com.epubreader.feature.settings

import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.GuidedThemePaletteInput
import com.epubreader.core.model.ThemePalette
import com.epubreader.core.model.generatePaletteFromBase
import com.epubreader.core.model.generatePaletteFromGuidedInput
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeEditorModeInferenceTest {

    @Test
    fun fromTheme_basicGeneratedPalette_reopensInBasic() {
        val theme = customTheme(
            palette = generatePaletteFromBase(
                primary = 0xFF4F46E5,
                background = 0xFFFAF9F6,
            ),
            isAdvanced = true,
        )

        assertEquals(ThemeEditorMode.BASIC, ThemeEditorDraft.fromTheme(theme).mode)
    }

    @Test
    fun fromTheme_guidedPaletteWithLinkedReader_reopensInExtended() {
        val theme = customTheme(
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFFA5B4FC,
                    appBackground = 0xFF0F172A,
                    chromeAccent = 0xFF94A3B8,
                    appSurface = 0xFF1E293B,
                    appForeground = 0xFFCBD5E1,
                    appForegroundMuted = 0xFF94A3B8,
                    overlayScrim = 0xFF000000,
                    readerLinked = true,
                ),
            ),
        )

        assertEquals(ThemeEditorMode.EXTENDED, ThemeEditorDraft.fromTheme(theme).mode)
    }

    @Test
    fun fromTheme_guidedPaletteWithUnlockedReader_reopensInExtended() {
        val theme = customTheme(
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF8B5E3C,
                    appBackground = 0xFFFDF6E3,
                    chromeAccent = 0xFF6D4C41,
                    appSurface = 0xFFF7EFD8,
                    appForeground = 0xFF5B4636,
                    appForegroundMuted = 0xFF8A7A6A,
                    readerBackground = 0xFFE7F5E9,
                    readerForeground = 0xFF243424,
                    overlayScrim = 0xFF1A130D,
                    readerLinked = false,
                ),
            ),
        )

        assertEquals(ThemeEditorMode.EXTENDED, ThemeEditorDraft.fromTheme(theme).mode)
    }

    @Test
    fun fromTheme_advancedOnlyOverride_reopensInAdvanced() {
        val basePalette = generatePaletteFromGuidedInput(
            GuidedThemePaletteInput(
                accent = 0xFF38BDF8,
                appBackground = 0xFF000000,
                chromeAccent = 0xFF94A3B8,
                appSurface = 0xFF000000,
                appForeground = 0xFFFFFFFF,
                appForegroundMuted = 0xFF94A3B8,
                overlayScrim = 0xFF000000,
                readerLinked = true,
            ),
        )
        val theme = customTheme(
            palette = basePalette.copy(favoriteAccent = 0xFFFF0000),
        )

        assertEquals(ThemeEditorMode.ADVANCED, ThemeEditorDraft.fromTheme(theme).mode)
    }

    @Test
    fun fromTheme_legacyAdvancedFlagDoesNotOverrideGuidedInference() {
        val theme = customTheme(
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF34D399,
                    appBackground = 0xFF064E3B,
                    chromeAccent = 0xFF059669,
                    appSurface = 0xFF065F46,
                    appForeground = 0xFFD1FAE5,
                    appForegroundMuted = 0xFF94D3B0,
                    overlayScrim = 0xFF00170F,
                    readerLinked = true,
                ),
            ),
            isAdvanced = true,
        )

        assertEquals(ThemeEditorMode.EXTENDED, ThemeEditorDraft.fromTheme(theme).mode)
    }

    private fun customTheme(
        palette: ThemePalette,
        isAdvanced: Boolean = false,
    ): CustomTheme {
        return CustomTheme(
            id = "custom-theme-test",
            name = "Test Theme",
            palette = palette,
            isAdvanced = isAdvanced,
        )
    }
}
