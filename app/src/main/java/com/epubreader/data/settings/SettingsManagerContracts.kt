package com.epubreader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.GlobalSettings

/**
 * AI_LOAD_STRATEGY
 * - Read this file when you need the SettingsManager key/default map or the public persistence shape.
 * - Read `SettingsManager.kt` for persistence behavior and edit transactions.
 * - Read `SettingsManagerJson.kt` only when folder/order/group JSON behavior is relevant.
 */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "epub_settings")

internal const val DefaultLibraryName = "My Library"
internal const val DefaultLibrarySort = "added_desc"
internal const val EmptyJsonObject = "{}"
internal const val EmptyJsonArray = "[]"

internal object SettingsPreferenceKeys {
    val fontSize = intPreferencesKey("global_font_size")
    val fontType = stringPreferencesKey("global_font_type")
    val theme = stringPreferencesKey("global_theme")
    val lineHeight = floatPreferencesKey("global_line_height")
    val horizontalPadding = intPreferencesKey("global_h_padding")
    val firstTime = booleanPreferencesKey("first_time")
    val lastSeenVersion = intPreferencesKey("last_seen_version")
    val showScrubber = booleanPreferencesKey("show_scrubber")
    val librarySort = stringPreferencesKey("library_sort")
    val favoriteLibrary = stringPreferencesKey("favorite_library")
    val bookGroups = stringPreferencesKey("book_groups")
    val folderSorts = stringPreferencesKey("folder_sorts")
    val folderOrder = stringPreferencesKey("folder_order")
}

internal data class BookProgressPreferenceKeys(
    val scrollIndex: Preferences.Key<Int>,
    val scrollOffset: Preferences.Key<Int>,
    val chapter: Preferences.Key<String>,
)

internal fun bookProgressPreferenceKeys(bookId: String): BookProgressPreferenceKeys {
    return BookProgressPreferenceKeys(
        scrollIndex = intPreferencesKey("${bookId}_scroll_index"),
        scrollOffset = intPreferencesKey("${bookId}_scroll_offset"),
        chapter = stringPreferencesKey("${bookId}_chapter"),
    )
}

internal fun Preferences.toGlobalSettings(): GlobalSettings {
    return GlobalSettings(
        fontSize = this[SettingsPreferenceKeys.fontSize] ?: 18,
        fontType = this[SettingsPreferenceKeys.fontType] ?: "serif",
        theme = this[SettingsPreferenceKeys.theme] ?: "light",
        lineHeight = this[SettingsPreferenceKeys.lineHeight] ?: 1.6f,
        horizontalPadding = this[SettingsPreferenceKeys.horizontalPadding] ?: 16,
        firstTime = this[SettingsPreferenceKeys.firstTime] ?: true,
        lastSeenVersionCode = this[SettingsPreferenceKeys.lastSeenVersion] ?: 0,
        showScrubber = this[SettingsPreferenceKeys.showScrubber] ?: false,
        librarySort = this[SettingsPreferenceKeys.librarySort] ?: DefaultLibrarySort,
        favoriteLibrary = this[SettingsPreferenceKeys.favoriteLibrary] ?: DefaultLibraryName,
        bookGroups = this[SettingsPreferenceKeys.bookGroups] ?: EmptyJsonObject,
        folderSorts = this[SettingsPreferenceKeys.folderSorts] ?: EmptyJsonObject,
        folderOrder = this[SettingsPreferenceKeys.folderOrder] ?: EmptyJsonArray,
    )
}

internal fun Preferences.toBookProgress(bookId: String): BookProgress {
    val keys = bookProgressPreferenceKeys(bookId)
    return BookProgress(
        scrollIndex = this[keys.scrollIndex] ?: 0,
        scrollOffset = this[keys.scrollOffset] ?: 0,
        lastChapterHref = this[keys.chapter],
    )
}
