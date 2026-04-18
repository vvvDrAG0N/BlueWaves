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
package com.epubreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.DarkThemeId
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.LightThemeId
import com.epubreader.core.model.isBuiltInTheme
import com.epubreader.core.model.normalizeThemeSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Manages all persistent storage using Jetpack DataStore.
 * Lifecycle: Singleton-like (bound to application context via Activity/ViewModel).
 *
 * AI_LOAD_STRATEGY
 * - Read this file for public persistence behavior and edit transactions.
 * - Read `SettingsManagerContracts.kt` for keys/defaults and data mapping.
 * - Read `SettingsManagerJson.kt` only when folder/group/order JSON mutations are involved.
 */
class SettingsManager(private val context: Context) {

    // AI_STATE_OWNER: GlobalSettings (Collected in UI for reactive updates)
    val globalSettings: Flow<GlobalSettings> = context.settingsDataStore.data.map { preferences ->
        preferences.toGlobalSettings()
    }.distinctUntilChanged()

    // Folder and library organization.

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
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.folderOrder] = order.toJsonArray().toString()
        }
    }

    /**
     * PURPOSE: Creates an empty folder and appends it to the saved custom order.
     * INPUT: folderName, default sort for the new folder.
     * OUTPUT: None (Suspending).
     * SIDE EFFECTS: Updates FOLDER_SORTS and FOLDER_ORDER in one transaction.
     */
    suspend fun createFolder(folderName: String, sort: String = DefaultLibrarySort) {
        context.settingsDataStore.edit { preferences ->
            val trimmedName = folderName.trim()
            if (trimmedName.isEmpty() || trimmedName == DefaultLibraryName) return@edit

            val folderSortsJson = safeJsonObject(preferences[SettingsPreferenceKeys.folderSorts])
            folderSortsJson.put(trimmedName, sort)
            preferences[SettingsPreferenceKeys.folderSorts] = folderSortsJson.toString()

            val existingOrder = safeJsonArray(preferences[SettingsPreferenceKeys.folderOrder]).toStringList()
            if (!existingOrder.contains(trimmedName)) {
                preferences[SettingsPreferenceKeys.folderOrder] =
                    (existingOrder + trimmedName).toJsonArray().toString()
            }
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
        context.settingsDataStore.edit { preferences ->
            if (folderName == null || folderName == DefaultLibraryName) {
                preferences[SettingsPreferenceKeys.librarySort] = sort
            } else {
                val currentJson = safeJsonObject(preferences[SettingsPreferenceKeys.folderSorts])
                currentJson.put(folderName, sort)
                preferences[SettingsPreferenceKeys.folderSorts] = currentJson.toString()
            }
        }
    }

    suspend fun setFavoriteLibrary(libraryName: String) {
        // AI_MUTATION_POINT
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.favoriteLibrary] = libraryName
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
        context.settingsDataStore.edit { preferences ->
            val currentJson = safeJsonObject(preferences[SettingsPreferenceKeys.bookGroups])
            if (groupName == null || groupName == DefaultLibraryName) {
                currentJson.remove(bookId)
            } else {
                currentJson.put(bookId, groupName)
            }
            preferences[SettingsPreferenceKeys.bookGroups] = currentJson.toString()
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
        context.settingsDataStore.edit { preferences ->
            val trimmedOldName = oldName.trim()
            val trimmedNewName = newName.trim()
            if (
                trimmedOldName.isEmpty() ||
                trimmedOldName == DefaultLibraryName ||
                trimmedNewName.isEmpty() ||
                trimmedNewName == trimmedOldName ||
                trimmedNewName == DefaultLibraryName
            ) {
                return@edit
            }

            // Update folder sorts
            val folderSortsJson = strictJsonObject(preferences[SettingsPreferenceKeys.folderSorts])
            val folderOrderJson = strictJsonArray(preferences[SettingsPreferenceKeys.folderOrder])
            val bookGroupsJson = strictJsonObject(preferences[SettingsPreferenceKeys.bookGroups])

            val existingFolders = linkedSetOf<String>()
            val sortKeys = folderSortsJson.keys()
            while (sortKeys.hasNext()) {
                existingFolders.add(sortKeys.next())
            }
            for (index in 0 until folderOrderJson.length()) {
                existingFolders.add(folderOrderJson.getString(index))
            }
            val groupKeys = bookGroupsJson.keys()
            while (groupKeys.hasNext()) {
                val key = groupKeys.next()
                val groupName = bookGroupsJson.optString(key)
                if (groupName.isNotEmpty()) {
                    existingFolders.add(groupName)
                }
            }
            preferences[SettingsPreferenceKeys.favoriteLibrary]
                ?.takeIf { it.isNotEmpty() && it != DefaultLibraryName }
                ?.let(existingFolders::add)

            if (existingFolders.any { it == trimmedNewName && it != trimmedOldName }) {
                return@edit
            }

            folderSortsJson.renameStringEntry(trimmedOldName, trimmedNewName)
            preferences[SettingsPreferenceKeys.folderSorts] = folderSortsJson.toString()

            // Update folder order
            preferences[SettingsPreferenceKeys.folderOrder] =
                folderOrderJson.replacingEntry(trimmedOldName, trimmedNewName).toString()

            // Update book groups
            bookGroupsJson.replaceStringValues(trimmedOldName, trimmedNewName)
            preferences[SettingsPreferenceKeys.bookGroups] = bookGroupsJson.toString()

            // Update favorite library if needed
            if (preferences[SettingsPreferenceKeys.favoriteLibrary] == trimmedOldName) {
                preferences[SettingsPreferenceKeys.favoriteLibrary] = trimmedNewName
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
        context.settingsDataStore.edit { preferences ->
            // Remove from folder sorts
            val folderSortsJson = strictJsonObject(preferences[SettingsPreferenceKeys.folderSorts])
            folderSortsJson.remove(folderName)
            preferences[SettingsPreferenceKeys.folderSorts] = folderSortsJson.toString()

            // Remove from folder order
            val folderOrderJson = strictJsonArray(preferences[SettingsPreferenceKeys.folderOrder])
            preferences[SettingsPreferenceKeys.folderOrder] = folderOrderJson.withoutEntry(folderName).toString()

            // Remove folder association from books (they move back to "My Library")
            val bookGroupsJson = strictJsonObject(preferences[SettingsPreferenceKeys.bookGroups])
            bookGroupsJson.removeEntriesWithStringValue(folderName)
            preferences[SettingsPreferenceKeys.bookGroups] = bookGroupsJson.toString()

            // Reset favorite library to "My Library" if the favorite was deleted
            if (preferences[SettingsPreferenceKeys.favoriteLibrary] == folderName) {
                preferences[SettingsPreferenceKeys.favoriteLibrary] = DefaultLibraryName
            }
        }
    }

    // App bootstrap and version tracking.
    suspend fun setFirstTime(firstTime: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.firstTime] = firstTime
        }
    }

    fun getLastSeenVersionCode(): Flow<Int> = context.settingsDataStore.data.map {
        it[SettingsPreferenceKeys.lastSeenVersion] ?: 0
    }

    suspend fun setLastSeenVersionCode(version: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.lastSeenVersion] = version
        }
    }

    // Global reader preferences.
    suspend fun updateGlobalSettings(transform: (GlobalSettings) -> GlobalSettings) {
        // AI_MUTATION_POINT
        context.settingsDataStore.edit { preferences ->
            val current = preferences.toGlobalSettings()
            val updated = transform(current)
            preferences[SettingsPreferenceKeys.fontSize] = updated.fontSize
            preferences[SettingsPreferenceKeys.fontType] = updated.fontType
            preferences[SettingsPreferenceKeys.theme] =
                normalizeThemeSelection(updated.theme, updated.customThemes)
            preferences[SettingsPreferenceKeys.customThemes] = updated.customThemes.toCustomThemesJson()
            preferences[SettingsPreferenceKeys.lineHeight] = updated.lineHeight
            preferences[SettingsPreferenceKeys.horizontalPadding] = updated.horizontalPadding
            preferences[SettingsPreferenceKeys.showScrubber] = updated.showScrubber
            preferences[SettingsPreferenceKeys.showSystemBar] = updated.showSystemBar
            preferences[SettingsPreferenceKeys.selectableText] = updated.selectableText
            preferences[SettingsPreferenceKeys.hapticFeedback] = updated.hapticFeedback
            preferences[SettingsPreferenceKeys.allowBlankCovers] = updated.allowBlankCovers
            preferences[SettingsPreferenceKeys.targetTranslationLanguage] = updated.targetTranslationLanguage
        }
    }

    suspend fun updateGlobalSettings(settings: GlobalSettings) {
        updateGlobalSettings { settings }
    }

    suspend fun setActiveTheme(themeId: String) {
        context.settingsDataStore.edit { preferences ->
            val customThemes = parseCustomThemes(preferences[SettingsPreferenceKeys.customThemes])
            preferences[SettingsPreferenceKeys.theme] = normalizeThemeSelection(themeId, customThemes)
        }
    }

    suspend fun saveCustomTheme(theme: CustomTheme, activate: Boolean = false) {
        context.settingsDataStore.edit { preferences ->
            val trimmedName = theme.name.trim()
            val trimmedId = theme.id.trim()
            if (trimmedName.isEmpty() || trimmedId.isEmpty() || isBuiltInTheme(trimmedId)) {
                return@edit
            }

            val existingThemes = parseCustomThemes(preferences[SettingsPreferenceKeys.customThemes])
            val normalizedTheme = theme.copy(id = trimmedId, name = trimmedName)
            val updatedThemes = if (existingThemes.any { it.id == normalizedTheme.id }) {
                existingThemes.map { existing ->
                    if (existing.id == normalizedTheme.id) normalizedTheme else existing
                }
            } else {
                existingThemes + normalizedTheme
            }

            preferences[SettingsPreferenceKeys.customThemes] = updatedThemes.toCustomThemesJson()
            if (activate || preferences[SettingsPreferenceKeys.theme] == normalizedTheme.id) {
                preferences[SettingsPreferenceKeys.theme] = normalizedTheme.id
            }
        }
    }

    suspend fun deleteCustomTheme(themeId: String) {
        context.settingsDataStore.edit { preferences ->
            val existingThemes = parseCustomThemes(preferences[SettingsPreferenceKeys.customThemes])
            val updatedThemes = existingThemes.filterNot { it.id == themeId }
            if (updatedThemes.size == existingThemes.size) {
                return@edit
            }

            preferences[SettingsPreferenceKeys.customThemes] = updatedThemes.toCustomThemesJson()
            if (preferences[SettingsPreferenceKeys.theme] == themeId) {
                preferences[SettingsPreferenceKeys.theme] = LightThemeId
            }
        }
    }

    suspend fun toggleTheme() {
        context.settingsDataStore.edit { preferences ->
            val customThemes = parseCustomThemes(preferences[SettingsPreferenceKeys.customThemes])
            val current = normalizeThemeSelection(preferences[SettingsPreferenceKeys.theme], customThemes)
            preferences[SettingsPreferenceKeys.theme] = if (current == DarkThemeId) {
                LightThemeId
            } else {
                DarkThemeId
            }
        }
    }

    suspend fun toggleShowSystemBar() {
        context.settingsDataStore.edit { preferences ->
            val current = preferences[SettingsPreferenceKeys.showSystemBar] ?: false
            preferences[SettingsPreferenceKeys.showSystemBar] = !current
        }
    }

    suspend fun toggleSelectableText() {
        context.settingsDataStore.edit { preferences ->
            val current = preferences[SettingsPreferenceKeys.selectableText] ?: false
            preferences[SettingsPreferenceKeys.selectableText] = !current
        }
    }

    // Per-book progress and cleanup.
    /**
     * PURPOSE: Retrieves current reading progress for a specific book.
     * INPUT: bookId.
     * OUTPUT: Flow of [BookProgress].
     * NOTES: Emits every time any value in DataStore changes.
     */
    fun getBookProgress(
        bookId: String,
        representation: BookRepresentation = BookRepresentation.EPUB,
    ): Flow<BookProgress> = context.settingsDataStore.data.map { preferences ->
        preferences.toBookProgress(bookId, representation)
    }

    /**
     * PURPOSE: Saves reading progress for a book.
     * INPUT: bookId, [BookProgress] object.
     * OUTPUT: None (Suspending).
     * SIDE EFFECTS: Updates three separate preference keys prefixed with bookId.
     * AI_WARNING: Frequent calls may cause excessive disk IO. Typically debounced in UI.
     * // AI_MUTATION_POINT: Saves reading progress to DataStore.
     */
    suspend fun saveBookProgress(
        bookId: String,
        progress: BookProgress,
        representation: BookRepresentation = BookRepresentation.EPUB,
    ) {
        // AI_MUTATION_POINT
        context.settingsDataStore.edit { preferences ->
            val keys = bookProgressPreferenceKeys(bookId, representation)
            preferences[keys.scrollIndex] = progress.scrollIndex
            preferences[keys.scrollOffset] = progress.scrollOffset
            if (progress.lastChapterHref == null) {
                preferences.remove(keys.chapter)
            } else {
                preferences[keys.chapter] = progress.lastChapterHref
            }

            if (representation == BookRepresentation.EPUB) {
                val legacyKeys = legacyBookProgressPreferenceKeys(bookId)
                preferences[legacyKeys.scrollIndex] = progress.scrollIndex
                preferences[legacyKeys.scrollOffset] = progress.scrollOffset
                if (progress.lastChapterHref == null) {
                    preferences.remove(legacyKeys.chapter)
                } else {
                    preferences[legacyKeys.chapter] = progress.lastChapterHref
                }
            }
        }
    }

    suspend fun deleteBookData(bookId: String) {
        context.settingsDataStore.edit { preferences ->
            BookRepresentation.entries.forEach { representation ->
                val keys = bookProgressPreferenceKeys(bookId, representation)
                preferences.remove(keys.scrollIndex)
                preferences.remove(keys.scrollOffset)
                preferences.remove(keys.chapter)
            }
            val legacyKeys = legacyBookProgressPreferenceKeys(bookId)
            preferences.remove(legacyKeys.scrollIndex)
            preferences.remove(legacyKeys.scrollOffset)
            preferences.remove(legacyKeys.chapter)

            val bookGroupsJson = safeJsonObject(preferences[SettingsPreferenceKeys.bookGroups])
            bookGroupsJson.remove(bookId)
            preferences[SettingsPreferenceKeys.bookGroups] = bookGroupsJson.toString()
        }
    }
}
