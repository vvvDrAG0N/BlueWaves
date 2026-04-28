package com.epubreader.feature.settings

import com.epubreader.core.model.GuidedThemePaletteInput
import com.epubreader.core.model.generatePaletteFromGuidedInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorPickerGuidanceTest {

    @Test
    fun buildGuidedSafeZone_samplesRealPreviewValidityForGuidedField() {
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

        val zone = buildGuidedSafeZone(
            hue = 240f,
            previewColor = { rawHex ->
                draft.previewColorEdit(
                    fieldKey = "app_foreground",
                    rawHex = rawHex,
                    guided = true,
                )
            },
        )

        assertTrue(zone.rows.isNotEmpty())
        assertTrue(
            zone.contains(
                ThemeColorPickerPoint(
                    saturation = 0f,
                    value = 1f,
                ),
            ),
        )
        assertFalse(
            zone.contains(
                ThemeColorPickerPoint(
                    saturation = 1f,
                    value = 0f,
                ),
            ),
        )
    }

    @Test
    fun project_outsidePoint_snapsToNearestAllowedSpan() {
        val zone = ThemeColorPickerSafeZone(
            rows = listOf(
                ThemeColorPickerSafeZoneRow(
                    value = 0.75f,
                    spans = listOf(0.20f..0.40f),
                ),
                ThemeColorPickerSafeZoneRow(
                    value = 0.50f,
                    spans = listOf(0.30f..0.60f),
                ),
            ),
        )

        val projected = zone.project(
            ThemeColorPickerPoint(
                saturation = 0.95f,
                value = 0.52f,
            ),
        )

        assertEquals(0.60f, projected.saturation, 0.0001f)
        assertEquals(0.50f, projected.value, 0.0001f)
    }

    @Test
    fun project_betweenRows_snapsValueToSampledRow() {
        val zone = ThemeColorPickerSafeZone(
            rows = listOf(
                ThemeColorPickerSafeZoneRow(
                    value = 0.75f,
                    spans = listOf(0.20f..0.40f),
                ),
                ThemeColorPickerSafeZoneRow(
                    value = 0.50f,
                    spans = listOf(0.30f..0.60f),
                ),
            ),
        )

        val projected = zone.project(
            ThemeColorPickerPoint(
                saturation = 0.35f,
                value = 0.52f,
            ),
        )

        assertEquals(0.35f, projected.saturation, 0.0001f)
        assertEquals(0.50f, projected.value, 0.0001f)
    }

    @Test
    fun safeZoneCache_reusesExistingZoneWithinRoundedHueBucket() {
        val cache = ThemeColorPickerSafeZoneCache()
        var buildCount = 0

        val first = cache.zoneForHue(12.2f) {
            buildCount += 1
            ThemeColorPickerSafeZone(
                rows = listOf(
                    ThemeColorPickerSafeZoneRow(
                        value = 0.5f,
                        spans = listOf(0.2f..0.8f),
                    ),
                ),
            )
        }
        val second = cache.zoneForHue(12.4f) {
            buildCount += 1
            ThemeColorPickerSafeZone(
                rows = listOf(
                    ThemeColorPickerSafeZoneRow(
                        value = 0.25f,
                        spans = listOf(0.1f..0.9f),
                    ),
                ),
            )
        }

        assertEquals(1, buildCount)
        assertSame(first, second)
    }

    @Test
    fun safeZoneCache_wrapsNearZeroHueIntoSameBucket() {
        val cache = ThemeColorPickerSafeZoneCache()
        var buildCount = 0

        val first = cache.zoneForHue(359.6f) {
            buildCount += 1
            ThemeColorPickerSafeZone(rows = emptyList())
        }
        val second = cache.zoneForHue(0.2f) {
            buildCount += 1
            ThemeColorPickerSafeZone(rows = emptyList())
        }

        assertEquals(1, buildCount)
        assertSame(first, second)
    }

    @Test
    fun resolveGuidedTypedHex_matchesCommitResolution() {
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

        val expected = draft.applyColorEdit(
            fieldKey = "app_foreground",
            rawHex = "#000000",
            guided = true,
        )

        val resolution = resolveGuidedTypedHex("#000000") { rawHex ->
            draft.previewColorEdit(
                fieldKey = "app_foreground",
                rawHex = rawHex,
                guided = true,
            )
        }

        assertTrue(resolution.wasAdjusted)
        assertEquals(expected.resolvedHex, resolution.resolvedHex)
    }

    private fun extendedDraft(
        palette: com.epubreader.core.model.ThemePalette,
        readerLinked: Boolean = true,
    ): ThemeEditorDraft {
        return ThemeEditorDraft.fromPalette(
            name = "Guided",
            palette = palette,
            mode = ThemeEditorMode.EXTENDED,
            readerLinked = readerLinked,
            legacyIsAdvanced = true,
        )
    }
}
