package com.epubreader.data.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun toBookProgress_returnsDefaultsForEmptyPreferences() {
        val progress = preferencesOf().toBookProgress("book-1")

        assertEquals(0, progress.scrollIndex)
        assertEquals(0, progress.scrollOffset)
        assertNull(progress.lastChapterHref)
    }

    @Test
    fun toBookProgress_readsValuesForRequestedBookOnly() {
        val requested = bookProgressPreferenceKeys("book-1")
        val otherBookIndexKey = intPreferencesKey("book-2_scroll_index")
        val preferences = preferencesOf(
            requested.scrollIndex to 12,
            requested.scrollOffset to 34,
            requested.chapter to "Text/ch2.xhtml",
            otherBookIndexKey to 999,
        )

        val progress = preferences.toBookProgress("book-1")

        assertEquals(12, progress.scrollIndex)
        assertEquals(34, progress.scrollOffset)
        assertEquals("Text/ch2.xhtml", progress.lastChapterHref)
    }
}
