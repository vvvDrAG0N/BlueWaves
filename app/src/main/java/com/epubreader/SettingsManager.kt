/**
 * AI_ENTRY_POINT
 * AI_READ_FIRST
 * AI_RELEVANT_TO: [Data Persistence, Settings, Reading Progress, Folder Management]
 * AI_STATE_OWNER: Single source of truth for all persisted data.
 * 
 * FILE: SettingsManager.kt
 * PURPOSE: Centralized persistence layer for application settings and user reading progress.
 * RESPONSIBILITIES:
 *  - Manages GlobalSettings (UI preferences, folder structures, library sorting) via Jetpack DataStore.
 *  - Persists per-book reading progress (scroll position and current chapter).
 *  - Handles folder-related data migrations (rename/delete).
 * NON-GOALS:
 *  - Does not handle file IO for EPUB content (see EpubParser).
 *  - Does not manage UI state directly (provides Flows for Composables to collect).
 * DEPENDENCIES: Jetpack DataStore (Preferences).
 * SIDE EFFECTS: Direct disk writes to "epub_settings" DataStore on every update.
 */
package com.epubreader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "epub_settings")

/**
 * Data class representing the global state of the application.
 * AI_NOTE: Adding new fields here requires updating [SettingsManager.updateGlobalSettings] 
 * and the [SettingsManager.globalSettings] mapping logic.
 */
data class GlobalSettings(
    val fontSize: Int = 18,
    val fontType: String = "serif",
    val theme: String = "light",
    val lineHeight: Float = 1.6f,
    val horizontalPadding: Int = 16,
    val firstTime: Boolean = true,
    val lastSeenVersionCode: Int = 0,
    val showScrubber: Boolean = false,
    val librarySort: String = "added_desc",
    val favoriteLibrary: String = "My Library",
    val bookGroups: String = "{}", // JSON string mapping bookId to groupName
    val folderSorts: String = "{}", // JSON string mapping folderName to sort string
    val folderOrder: String = "[]" // JSON array of folder names for custom ordering
)

/**
 * Data class for per-book reading progress.
 * AI_NOTE: scrollIndex refers to the first visible item in the Reader's LazyColumn.
 */
data class BookProgress(
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val lastChapterHref: String? = null
)

/**
 * Manages all persistent storage using Jetpack DataStore.
 * Lifecycle: Singleton-like (bound to application context via Activity/ViewModel).
 */
class SettingsManager(private val context: Context) {

    // Global Preferences Keys
    private val FONT_SIZE = intPreferencesKey("global_font_size")
    private val FONT_TYPE = stringPreferencesKey("global_font_type")
    private val THEME = stringPreferencesKey("global_theme")
    private val LINE_HEIGHT = floatPreferencesKey("global_line_height")
    private val H_PADDING = intPreferencesKey("global_h_padding")
    private val FIRST_TIME = booleanPreferencesKey("first_time")
    private val LAST_SEEN_VERSION = intPreferencesKey("last_seen_version")
    private val SHOW_SCRUBBER = booleanPreferencesKey("show_scrubber")
    private val LIBRARY_SORT = stringPreferencesKey("library_sort")
    private val FAVORITE_LIBRARY = stringPreferencesKey("favorite_library")
    private val BOOK_GROUPS = stringPreferencesKey("book_groups")
    private val FOLDER_SORTS = stringPreferencesKey("folder_sorts")
    private val FOLDER_ORDER = stringPreferencesKey("folder_order")

    // AI_STATE_OWNER: GlobalSettings (Collected in UI for reactive updates)
    val globalSettings: Flow<GlobalSettings> = context.dataStore.data.map { preferences ->
        GlobalSettings(
            fontSize = preferences[FONT_SIZE] ?: 18,
            fontType = preferences[FONT_TYPE] ?: "serif",
            theme = preferences[THEME] ?: "light",
            lineHeight = preferences[LINE_HEIGHT] ?: 1.6f,
            horizontalPadding = preferences[H_PADDING] ?: 16,
            firstTime = preferences[FIRST_TIME] ?: true,
            lastSeenVersionCode = preferences[LAST_SEEN_VERSION] ?: 0,
            showScrubber = preferences[SHOW_SCRUBBER] ?: false,
            librarySort = preferences[LIBRARY_SORT] ?: "added_desc",
            favoriteLibrary = preferences[FAVORITE_LIBRARY] ?: "My Library",
            bookGroups = preferences[BOOK_GROUPS] ?: "{}",
            folderSorts = preferences[FOLDER_SORTS] ?: "{}",
            folderOrder = preferences[FOLDER_ORDER] ?: "[]"
        )
    }

    /**
     * PURPOSE: Updates the custom display order of folders in the library drawer.
     * INPUT: List of folder names in desired order.
     * OUTPUT: None (Suspending).
     * SIDE EFFECTS: Updates FOLDER_ORDER in DataStore.
     * // AI_MUTATION_POINT: Modifies global folder order.
     * // AI_WARNING: Must trigger UI refresh after this.
     */
    suspend fun updateFolderOrder(order: List<String>) {
        // AI_MUTATION_POINT
        context.dataStore.edit { preferences ->
            val jsonArray = org.json.JSONArray()
            order.forEach { jsonArray.put(it) }
            preferences[FOLDER_ORDER] = jsonArray.toString()
        }
    }

    /**
     * PURPOSE: Sets the sorting preference for a specific folder.
     * INPUT: folderName (null/My Library for default), sort string (e.g., "title_asc").
     * OUTPUT: None (Suspending).
     * SIDE EFFECTS: Updates either LIBRARY_SORT or FOLDER_SORTS JSON.
     * // AI_MUTATION_POINT: Modifies folder sort preferences.
     * // AI_WARNING: Must trigger UI refresh after this.
     */
    suspend fun setLibrarySort(folderName: String?, sort: String) {
        // AI_MUTATION_POINT
        context.dataStore.edit { preferences ->
            if (folderName == null || folderName == "My Library") {
                preferences[LIBRARY_SORT] = sort
            } else {
                val currentJson = try {
                    org.json.JSONObject(preferences[FOLDER_SORTS] ?: "{}")
                } catch (e: Exception) {
                    org.json.JSONObject("{}")
                }
                currentJson.put(folderName, sort)
                preferences[FOLDER_SORTS] = currentJson.toString()
            }
        }
    }

    suspend fun setFavoriteLibrary(libraryName: String) {
        // AI_MUTATION_POINT
        context.dataStore.edit { preferences ->
            preferences[FAVORITE_LIBRARY] = libraryName
        }
    }

    /**
     * PURPOSE: Moves a book to a folder or removes it from all folders.
     * INPUT: bookId (MD5 hash), groupName (target folder or null/"My Library" to reset).
     * OUTPUT: None (Suspending).
     * SIDE EFFECTS: Modifies the BOOK_GROUPS JSON mapping.
     * // AI_MUTATION_POINT: Modifies book folder assignment.
     * // AI_WARNING: Must trigger UI refresh after this.
     */
    suspend fun updateBookGroup(bookId: String, groupName: String?) {
        // AI_MUTATION_POINT
        context.dataStore.edit { preferences ->
            val currentJson = try {
                org.json.JSONObject(preferences[BOOK_GROUPS] ?: "{}")
            } catch (e: Exception) {
                org.json.JSONObject("{}")
            }
            if (groupName == null || groupName == "My Library") {
                currentJson.remove(bookId)
            } else {
                currentJson.put(bookId, groupName)
            }
            preferences[BOOK_GROUPS] = currentJson.toString()
        }
    }

    /**
     * PURPOSE: Renames a folder and updates all associated metadata (sorts, order, book associations).
     * INPUT: oldName, newName.
     * OUTPUT: None (Suspending).
     * SIDE EFFECTS: Cascading updates to multiple DataStore keys.
     * AI_WARNING: This is a complex operation that modifies three separate JSON structures.
     * // AI_MUTATION_POINT: Bulk rename affecting folders and books.
     * // AI_WARNING: Must trigger UI refresh after this.
     */
    suspend fun renameFolder(oldName: String, newName: String) {
        // AI_MUTATION_POINT
        context.dataStore.edit { preferences ->
            // Update folder sorts
            val folderSortsJson = org.json.JSONObject(preferences[FOLDER_SORTS] ?: "{}")
            if (folderSortsJson.has(oldName)) {
                val sort = folderSortsJson.getString(oldName)
                folderSortsJson.remove(oldName)
                folderSortsJson.put(newName, sort)
                preferences[FOLDER_SORTS] = folderSortsJson.toString()
            }

            // Update folder order
            val folderOrderJson = org.json.JSONArray(preferences[FOLDER_ORDER] ?: "[]")
            val newOrder = org.json.JSONArray()
            for (i in 0 until folderOrderJson.length()) {
                val name = folderOrderJson.getString(i)
                if (name == oldName) newOrder.put(newName) else newOrder.put(name)
            }
            preferences[FOLDER_ORDER] = newOrder.toString()

            // Update book groups
            val bookGroupsJson = org.json.JSONObject(preferences[BOOK_GROUPS] ?: "{}")
            val keys = bookGroupsJson.keys()
            while (keys.hasNext()) {
                val bookId = keys.next()
                if (bookGroupsJson.getString(bookId) == oldName) {
                    bookGroupsJson.put(bookId, newName)
                }
            }
            preferences[BOOK_GROUPS] = bookGroupsJson.toString()

            // Update favorite library if needed
            if (preferences[FAVORITE_LIBRARY] == oldName) {
                preferences[FAVORITE_LIBRARY] = newName
            }
        }
    }

    /**
     * PURPOSE: Deletes a folder and moves all its books back to "My Library".
     * INPUT: folderName to delete.
     * OUTPUT: None (Suspending).
     * SIDE EFFECTS: Updates sorts, order, and book groups. Resets favorite library if it matches.
     * // AI_MUTATION_POINT: Deletes folder and orphans its contents.
     * // AI_WARNING: Must trigger UI refresh after this.
     */
    suspend fun deleteFolder(folderName: String) {
        // AI_MUTATION_POINT
        context.dataStore.edit { preferences ->
            // Remove from folder sorts
            val folderSortsJson = org.json.JSONObject(preferences[FOLDER_SORTS] ?: "{}")
            folderSortsJson.remove(folderName)
            preferences[FOLDER_SORTS] = folderSortsJson.toString()

            // Remove from folder order
            val folderOrderJson = org.json.JSONArray(preferences[FOLDER_ORDER] ?: "[]")
            val newOrder = org.json.JSONArray()
            for (i in 0 until folderOrderJson.length()) {
                val name = folderOrderJson.getString(i)
                if (name != folderName) newOrder.put(name)
            }
            preferences[FOLDER_ORDER] = newOrder.toString()

            // Remove folder association from books (they move back to "My Library")
            val bookGroupsJson = org.json.JSONObject(preferences[BOOK_GROUPS] ?: "{}")
            val keys = bookGroupsJson.keys().asSequence().toList()
            keys.forEach { bookId ->
                if (bookGroupsJson.getString(bookId) == folderName) {
                    bookGroupsJson.remove(bookId)
                }
            }
            preferences[BOOK_GROUPS] = bookGroupsJson.toString()

            // Reset favorite library to "My Library" if the favorite was deleted
            if (preferences[FAVORITE_LIBRARY] == folderName) {
                preferences[FAVORITE_LIBRARY] = "My Library"
            }
        }
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
        // AI_MUTATION_POINT
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE] = settings.fontSize
            preferences[FONT_TYPE] = settings.fontType
            preferences[THEME] = settings.theme
            preferences[LINE_HEIGHT] = settings.lineHeight
            preferences[H_PADDING] = settings.horizontalPadding
            preferences[SHOW_SCRUBBER] = settings.showScrubber
        }
    }

    suspend fun toggleTheme() {
        context.dataStore.edit { preferences ->
            val current = preferences[THEME] ?: "light"
            preferences[THEME] = if (current == "dark") "light" else "dark"
        }
    }

    /**
     * PURPOSE: Retrieves current reading progress for a specific book.
     * INPUT: bookId.
     * OUTPUT: Flow of [BookProgress].
     * NOTES: Emits every time any value in DataStore changes.
     */
    fun getBookProgress(bookId: String): Flow<BookProgress> = context.dataStore.data.map { preferences ->
        BookProgress(
            scrollIndex = preferences[intPreferencesKey("${bookId}_scroll_index")] ?: 0,
            scrollOffset = preferences[intPreferencesKey("${bookId}_scroll_offset")] ?: 0,
            lastChapterHref = preferences[stringPreferencesKey("${bookId}_chapter")]
        )
    }

    /**
     * PURPOSE: Saves reading progress for a book.
     * INPUT: bookId, [BookProgress] object.
     * OUTPUT: None (Suspending).
     * SIDE EFFECTS: Updates three separate preference keys prefixed with bookId.
     * AI_WARNING: Frequent calls may cause excessive disk IO. Typically debounced in UI.
     * // AI_MUTATION_POINT: Saves reading progress to DataStore.
     */
    suspend fun saveBookProgress(bookId: String, progress: BookProgress) {
        // AI_MUTATION_POINT
        context.dataStore.edit { preferences ->
            preferences[intPreferencesKey("${bookId}_scroll_index")] = progress.scrollIndex
            preferences[intPreferencesKey("${bookId}_scroll_offset")] = progress.scrollOffset
            if (progress.lastChapterHref == null) {
                preferences.remove(stringPreferencesKey("${bookId}_chapter"))
            } else {
                preferences[stringPreferencesKey("${bookId}_chapter")] = progress.lastChapterHref
            }
        }
    }

    suspend fun deleteBookData(bookId: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(intPreferencesKey("${bookId}_scroll_index"))
            preferences.remove(intPreferencesKey("${bookId}_scroll_offset"))
            preferences.remove(stringPreferencesKey("${bookId}_chapter"))
            
            val bookGroupsJson = try {
                org.json.JSONObject(preferences[BOOK_GROUPS] ?: "{}")
            } catch (e: Exception) {
                org.json.JSONObject("{}")
            }
            bookGroupsJson.remove(bookId)
            preferences[BOOK_GROUPS] = bookGroupsJson.toString()
        }
    }
}
