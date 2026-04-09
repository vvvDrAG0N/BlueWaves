package com.epubreader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "epub_settings")

data class GlobalSettings(
    val fontSize: Int = 18,
    val fontType: String = "serif",
    val theme: String = "light",
    val lineHeight: Float = 1.6f,
    val horizontalPadding: Int = 16,
    val firstTime: Boolean = true,
    val lastSeenVersionCode: Int = 0
)

data class BookProgress(
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val lastChapterHref: String? = null
)

class SettingsManager(private val context: Context) {

    // Global Preferences Keys
    private val FONT_SIZE = intPreferencesKey("global_font_size")
    private val FONT_TYPE = stringPreferencesKey("global_font_type")
    private val THEME = stringPreferencesKey("global_theme")
    private val LINE_HEIGHT = floatPreferencesKey("global_line_height")
    private val H_PADDING = intPreferencesKey("global_h_padding")
    private val FIRST_TIME = booleanPreferencesKey("first_time")
    private val LAST_SEEN_VERSION = intPreferencesKey("last_seen_version")

    val globalSettings: Flow<GlobalSettings> = context.dataStore.data.map { preferences ->
        GlobalSettings(
            fontSize = preferences[FONT_SIZE] ?: 18,
            fontType = preferences[FONT_TYPE] ?: "serif",
            theme = preferences[THEME] ?: "light",
            lineHeight = preferences[LINE_HEIGHT] ?: 1.6f,
            horizontalPadding = preferences[H_PADDING] ?: 16,
            firstTime = preferences[FIRST_TIME] ?: true,
            lastSeenVersionCode = preferences[LAST_SEEN_VERSION] ?: 0
        )
    }

    suspend fun setFirstTime(firstTime: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FIRST_TIME] = firstTime
        }
    }

    fun getLastSeenVersionCode(): Flow<Int> = context.dataStore.data.map { it[LAST_SEEN_VERSION] ?: 0 }

    suspend fun setLastSeenVersionCode(version: Int) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SEEN_VERSION] = version
        }
    }

    suspend fun updateGlobalSettings(settings: GlobalSettings) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE] = settings.fontSize
            preferences[FONT_TYPE] = settings.fontType
            preferences[THEME] = settings.theme
            preferences[LINE_HEIGHT] = settings.lineHeight
            preferences[H_PADDING] = settings.horizontalPadding
        }
    }

    suspend fun toggleTheme() {
        context.dataStore.edit { preferences ->
            val current = preferences[THEME] ?: "light"
            preferences[THEME] = if (current == "dark") "light" else "dark"
        }
    }

    // Per-Book Progress
    fun getBookProgress(bookId: String): Flow<BookProgress> = context.dataStore.data.map { preferences ->
        BookProgress(
            scrollIndex = preferences[intPreferencesKey("${bookId}_scroll_index")] ?: 0,
            scrollOffset = preferences[intPreferencesKey("${bookId}_scroll_offset")] ?: 0,
            lastChapterHref = preferences[stringPreferencesKey("${bookId}_chapter")]
        )
    }

    suspend fun saveBookProgress(bookId: String, progress: BookProgress) {
        context.dataStore.edit { preferences ->
            preferences[intPreferencesKey("${bookId}_scroll_index")] = progress.scrollIndex
            preferences[intPreferencesKey("${bookId}_scroll_offset")] = progress.scrollOffset
            progress.lastChapterHref?.let {
                preferences[stringPreferencesKey("${bookId}_chapter")] = it
            }
        }
    }
}
