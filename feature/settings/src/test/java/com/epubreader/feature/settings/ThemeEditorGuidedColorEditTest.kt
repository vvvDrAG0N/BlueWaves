package com.epubreader.feature.settings

import com.epubreader.core.model.GuidedThemePaletteInput
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromGuidedInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeEditorGuidedColorEditTest {

    @Test
    fun applyColorEdit_blackAppTextOnBlackBackground_adjustsAndReports() {
        val draft = extendedDraft(
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF4F46E5,
                    appBackground = 0xFF000000,
                    appSurface = 0xFF000000,
                    appForeground = 0xFFFFFFFF,
                    appForegroundMuted = 0xFFAAAAAA,
                    overlayScrim = 0xFF000000,
                    readerLinked = true,
                ),
            ),
        )

        val result = draft.applyColorEdit(
            fieldKey = "app_foreground",
            rawHex = "#000000",
            guided = true,
        )

        val expected = generatePaletteFromGuidedInput(
            GuidedThemePaletteInput(
                accent = 0xFF4F46E5,
                appBackground = 0xFF000000,
                appSurface = 0xFF000000,
                appForeground = 0xFF000000,
                appForegroundMuted = 0xFFAAAAAA,
                overlayScrim = 0xFF000000,
                readerLinked = true,
            ),
        )

        assertTrue(result.wasAdjusted)
        assertEquals(formatThemeColor(expected.appForeground), result.resolvedHex)
        assertEquals(result.resolvedHex, result.updatedDraft.appForeground)
    }

    @Test
    fun applyColorEdit_validAppText_preservesExactChoice() {
        val draft = extendedDraft(
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF4F46E5,
                    appBackground = 0xFFFFFFFF,
                    appSurface = 0xFFFFFFFF,
                    appForeground = 0xFF18181B,
                    appForegroundMuted = 0xFF71717A,
                    overlayScrim = 0xFF0F172A,
                    readerLinked = true,
                ),
            ),
        )

        val result = draft.applyColorEdit(
            fieldKey = "app_foreground",
            rawHex = "#000000",
            guided = true,
        )

        assertFalse(result.wasAdjusted)
        assertEquals("#000000", result.resolvedHex)
        assertEquals("#000000", result.updatedDraft.appForeground)
    }

    @Test
    fun applyColorEdit_mutedTextOnBlackBackground_adjustsAndReports() {
        val draft = extendedDraft(
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF4F46E5,
                    appBackground = 0xFF000000,
                    appSurface = 0xFF000000,
                    appForeground = 0xFFFFFFFF,
                    appForegroundMuted = 0xFFAAAAAA,
                    overlayScrim = 0xFF000000,
                    readerLinked = true,
                ),
            ),
        )

        val result = draft.applyColorEdit(
            fieldKey = "app_foreground_muted",
            rawHex = "#000000",
            guided = true,
        )

        val expected = generatePaletteFromGuidedInput(
            GuidedThemePaletteInput(
                accent = 0xFF4F46E5,
                appBackground = 0xFF000000,
                appSurface = 0xFF000000,
                appForeground = 0xFFFFFFFF,
                appForegroundMuted = 0xFF000000,
                overlayScrim = 0xFF000000,
                readerLinked = true,
            ),
        )

        assertTrue(result.wasAdjusted)
        assertEquals(formatThemeColor(expected.appForegroundMuted), result.resolvedHex)
    }

    @Test
    fun applyColorEdit_chromeAccentOnBlackBackground_adjustsAndReports() {
        val draft = extendedDraft(
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF4F46E5,
                    appBackground = 0xFF000000,
                    appSurface = 0xFF000000,
                    appForeground = 0xFFFFFFFF,
                    appForegroundMuted = 0xFFAAAAAA,
                    overlayScrim = 0xFF000000,
                    readerLinked = true,
                ),
            ),
        )

        val result = draft.applyColorEdit(
            fieldKey = "chrome_accent",
            rawHex = "#000000",
            guided = true,
        )

        val expected = generatePaletteFromGuidedInput(
            GuidedThemePaletteInput(
                accent = 0xFF4F46E5,
                appBackground = 0xFF000000,
                chromeAccent = 0xFF000000,
                appSurface = 0xFF000000,
                appForeground = 0xFFFFFFFF,
                appForegroundMuted = 0xFFAAAAAA,
                overlayScrim = 0xFF000000,
                readerLinked = true,
            ),
        )

        assertTrue(result.wasAdjusted)
        assertEquals(formatThemeColor(expected.chromeAccent), result.resolvedHex)
    }

    @Test
    fun applyColorEdit_unlinkedReaderTextOnBlackReaderPage_adjustsAndReports() {
        val draft = extendedDraft(
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF4F46E5,
                    appBackground = 0xFF202020,
                    chromeAccent = 0xFF909090,
                    appSurface = 0xFF202020,
                    appForeground = 0xFFFFFFFF,
                    appForegroundMuted = 0xFFAAAAAA,
                    readerBackground = 0xFF000000,
                    readerForeground = 0xFFFFFFFF,
                    overlayScrim = 0xFF000000,
                    readerLinked = false,
                ),
            ),
            readerLinked = false,
        )

        val result = draft.applyColorEdit(
            fieldKey = "reader_foreground",
            rawHex = "#000000",
            guided = true,
        )

        val expected = generatePaletteFromGuidedInput(
            GuidedThemePaletteInput(
                accent = 0xFF4F46E5,
                appBackground = 0xFF202020,
                chromeAccent = 0xFF909090,
                appSurface = 0xFF202020,
                appForeground = 0xFFFFFFFF,
                appForegroundMuted = 0xFFAAAAAA,
                readerBackground = 0xFF000000,
                readerForeground = 0xFF000000,
                overlayScrim = 0xFF000000,
                readerLinked = false,
            ),
        )

        assertTrue(result.wasAdjusted)
        assertEquals(formatThemeColor(expected.readerForeground), result.resolvedHex)
    }

    @Test
    fun previewColorEdit_doesNotMutateDraftBeforeCommit() {
        val draft = extendedDraft(
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF4F46E5,
                    appBackground = 0xFF000000,
                    appSurface = 0xFF000000,
                    appForeground = 0xFFFFFFFF,
                    appForegroundMuted = 0xFFAAAAAA,
                    overlayScrim = 0xFF000000,
                    readerLinked = true,
                ),
            ),
        )

        val preview = draft.previewColorEdit(
            fieldKey = "app_foreground",
            rawHex = "#000000",
            guided = true,
        )

        assertTrue(preview.wasAdjusted)
        assertEquals("#FFFFFF", draft.appForeground)
    }

    private fun extendedDraft(
        palette: com.epubreader.core.model.ThemePalette,
        readerLinked: Boolean = true,
    ): ThemeEditorDraft {
        return ThemeEditorDraft.fromPalette(
            name = "Test Theme",
            palette = palette,
            mode = ThemeEditorMode.EXTENDED,
            readerLinked = readerLinked,
            legacyIsAdvanced = true,
        )
    }
}
