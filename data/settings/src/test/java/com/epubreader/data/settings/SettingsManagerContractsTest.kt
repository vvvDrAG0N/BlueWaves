package com.epubreader.data.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.LightThemeId
import com.epubreader.core.model.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray

class SettingsManagerContractsTest {

    @Test
    fun toGlobalSettings_returnsDefaultsForEmptyPreferences() {
        val settings = preferencesOf().toGlobalSettings()

        assertEquals(18, settings.fontSize)
        assertEquals("serif", settings.fontType)
        assertEquals("light", settings.theme)
        assertEquals(1.6f, settings.lineHeight)
        assertEquals(16, settings.horizontalPadding)
        assertEquals(true, settings.firstTime)
        assertEquals(0, settings.lastSeenVersionCode)
        assertEquals(false, settings.showScrubber)
        assertEquals(false, settings.showSystemBar)
        assertEquals(false, settings.allowBlankCovers)
        assertTrue(settings.customThemes.isEmpty())
        assertEquals(DefaultLibrarySort, settings.librarySort)
        assertEquals(DefaultLibraryName, settings.favoriteLibrary)
        assertEquals(EmptyJsonObject, settings.bookGroups)
        assertEquals(EmptyJsonObject, settings.folderSorts)
        assertEquals(EmptyJsonArray, settings.folderOrder)
    }

    @Test
    fun toGlobalSettings_usesDefaultsForMissingKeysWhenPreferencesArePartial() {
        val preferences = preferencesOf(
            SettingsPreferenceKeys.theme to "dark",
            SettingsPreferenceKeys.fontSize to 24,
            SettingsPreferenceKeys.favoriteLibrary to "Sci-Fi",
        )

        val settings = preferences.toGlobalSettings()

        assertEquals(24, settings.fontSize)
        assertEquals("dark", settings.theme)
        assertEquals("Sci-Fi", settings.favoriteLibrary)
        assertEquals("serif", settings.fontType)
        assertEquals(1.6f, settings.lineHeight)
        assertEquals(DefaultLibrarySort, settings.librarySort)
        assertEquals(EmptyJsonArray, settings.folderOrder)
    }

    @Test
    fun toGlobalSettings_readsCustomThemesAndKeepsKnownCustomSelection() {
        val settings = preferencesOf(
            SettingsPreferenceKeys.theme to "custom-ocean",
            SettingsPreferenceKeys.customThemes to """
                [
                  {
                    "id": "custom-ocean",
                    "name": "Ocean",
                    "primary": "#2A6F97",
                    "secondary": "#468FAF",
                    "background": "#F4FAFF",
                    "surface": "#FFFFFF",
                    "surfaceVariant": "#D7EAF7",
                    "outline": "#8AA7BB",
                    "readerBackground": "#EEF8FF",
                    "readerForeground": "#10212D"
                  }
                ]
            """.trimIndent(),
        ).toGlobalSettings()

        assertEquals("custom-ocean", settings.theme)
        assertEquals(
            listOf(
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
            settings.customThemes,
        )
    }

    @Test
    fun toGlobalSettings_readsSemanticThemeKeysAndExpandedRoles() {
        val settings = preferencesOf(
            SettingsPreferenceKeys.theme to "custom-dawn",
            SettingsPreferenceKeys.customThemes to """
                [
                  {
                    "id": "custom-dawn",
                    "name": "Dawn",
                    "accent": "#7C3AED",
                    "chromeAccent": "#A855F7",
                    "appBackground": "#111827",
                    "appSurface": "#1F2937",
                    "appSurfaceVariant": "#374151",
                    "appOutline": "#6B7280",
                    "appForeground": "#F9FAFB",
                    "appForegroundMuted": "#CBD5E1",
                    "readerBackground": "#F4ECD8",
                    "readerForeground": "#5B4636",
                    "readerForegroundMuted": "#8C7767",
                    "readerAccent": "#8B5E3C",
                    "overlayScrim": "#0F172A",
                    "startupBackground": "#101826",
                    "startupForeground": "#F8FAFC",
                    "favoriteAccent": "#FFC857",
                    "coverOverlayScrim": "#09111C"
                  }
                ]
            """.trimIndent(),
        ).toGlobalSettings()

        assertEquals("custom-dawn", settings.theme)
        assertEquals(1, settings.customThemes.size)
        assertEquals(
            ThemePalette(
                primary = 0xFF7C3AED,
                secondary = 0xFFA855F7,
                background = 0xFF111827,
                surface = 0xFF1F2937,
                surfaceVariant = 0xFF374151,
                outline = 0xFF6B7280,
                readerBackground = 0xFFF4ECD8,
                readerForeground = 0xFF5B4636,
                systemForeground = 0xFFF9FAFB,
                appForegroundMuted = 0xFFCBD5E1,
                readerForegroundMuted = 0xFF8C7767,
                readerAccent = 0xFF8B5E3C,
                overlayScrim = 0xFF0F172A,
                startupBackground = 0xFF101826,
                startupForeground = 0xFFF8FAFC,
                favoriteAccent = 0xFFFFC857,
                coverOverlayScrim = 0xFF09111C,
            ),
            settings.customThemes.single().palette,
        )
    }

    @Test
    fun toGlobalSettings_fallsBackToLightWhenStoredThemeDoesNotExist() {
        val settings = preferencesOf(
            SettingsPreferenceKeys.theme to "custom-missing",
            SettingsPreferenceKeys.customThemes to EmptyJsonArray,
        ).toGlobalSettings()

        assertEquals(LightThemeId, settings.theme)
    }

    @Test
    fun toGlobalSettings_readsShowSystemBarWhenPresent() {
        val settings = preferencesOf(
            SettingsPreferenceKeys.showSystemBar to true,
            SettingsPreferenceKeys.allowBlankCovers to true,
        ).toGlobalSettings()

        assertEquals(true, settings.showSystemBar)
        assertEquals(true, settings.allowBlankCovers)
    }

    @Test
    fun toBookProgress_returnsDefaultsForEmptyPreferences() {
        val progress = preferencesOf().toBookProgress("book-1", BookRepresentation.EPUB)

        assertEquals(0, progress.scrollIndex)
        assertEquals(0, progress.scrollOffset)
        assertNull(progress.lastChapterHref)
    }

    @Test
    fun toCustomThemesJson_writesLegacyAndSemanticThemeKeys() {
        val json = listOf(
            CustomTheme(
                id = "custom-dawn",
                name = "Dawn",
                palette = ThemePalette(
                    primary = 0xFF7C3AED,
                    secondary = 0xFFA855F7,
                    background = 0xFF111827,
                    surface = 0xFF1F2937,
                    surfaceVariant = 0xFF374151,
                    outline = 0xFF6B7280,
                    readerBackground = 0xFFF4ECD8,
                    readerForeground = 0xFF5B4636,
                    systemForeground = 0xFFF9FAFB,
                    appForegroundMuted = 0xFFCBD5E1,
                    readerForegroundMuted = 0xFF8C7767,
                    readerAccent = 0xFF8B5E3C,
                    overlayScrim = 0xFF0F172A,
                    startupBackground = 0xFF101826,
                    startupForeground = 0xFFF8FAFC,
                    favoriteAccent = 0xFFFFC857,
                    coverOverlayScrim = 0xFF09111C,
                ),
            ),
        ).toCustomThemesJson()

        val item = JSONArray(json).getJSONObject(0)

        assertEquals("#7C3AED", item.getString("primary"))
        assertEquals("#7C3AED", item.getString("accent"))
        assertEquals("#111827", item.getString("background"))
        assertEquals("#111827", item.getString("appBackground"))
        assertEquals("#F9FAFB", item.getString("systemForeground"))
        assertEquals("#F9FAFB", item.getString("appForeground"))
        assertEquals("#CBD5E1", item.getString("appForegroundMuted"))
        assertEquals("#8C7767", item.getString("readerForegroundMuted"))
        assertEquals("#8B5E3C", item.getString("readerAccent"))
        assertEquals("#0F172A", item.getString("overlayScrim"))
        assertEquals("#101826", item.getString("startupBackground"))
        assertEquals("#F8FAFC", item.getString("startupForeground"))
        assertEquals("#FFC857", item.getString("favoriteAccent"))
        assertEquals("#09111C", item.getString("coverOverlayScrim"))
    }

    @Test
    fun toBookProgress_readsValuesForRequestedBookOnly() {
        val requested = bookProgressPreferenceKeys("book-1", BookRepresentation.EPUB)
        val otherBookIndexKey = intPreferencesKey("book-2_scroll_index")
        val preferences = preferencesOf(
            requested.scrollIndex to 12,
            requested.scrollOffset to 34,
            requested.chapter to "Text/ch2.xhtml",
            otherBookIndexKey to 999,
        )

        val progress = preferences.toBookProgress("book-1", BookRepresentation.EPUB)

        assertEquals(12, progress.scrollIndex)
        assertEquals(34, progress.scrollOffset)
        assertEquals("Text/ch2.xhtml", progress.lastChapterHref)
    }

    @Test
    fun toBookProgress_fallsBackToLegacyKeysForEpubRepresentation() {
        val legacyKeys = legacyBookProgressPreferenceKeys("book-1")
        val preferences = preferencesOf(
            legacyKeys.scrollIndex to 7,
            legacyKeys.scrollOffset to 19,
            legacyKeys.chapter to "Text/legacy.xhtml",
        )

        val progress = preferences.toBookProgress("book-1", BookRepresentation.EPUB)

        assertEquals(7, progress.scrollIndex)
        assertEquals(19, progress.scrollOffset)
        assertEquals("Text/legacy.xhtml", progress.lastChapterHref)
    }

    @Test
    fun toBookProgress_keepsPdfRepresentationIsolatedFromLegacyEpubKeys() {
        val legacyKeys = legacyBookProgressPreferenceKeys("book-1")
        val preferences = preferencesOf(
            legacyKeys.scrollIndex to 7,
            legacyKeys.scrollOffset to 19,
            legacyKeys.chapter to "Text/legacy.xhtml",
        )

        val progress = preferences.toBookProgress("book-1", BookRepresentation.PDF)

        assertEquals(0, progress.scrollIndex)
        assertEquals(0, progress.scrollOffset)
        assertNull(progress.lastChapterHref)
    }
}
